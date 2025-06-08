/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.util.countSubstring
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import org.spongepowered.configurate.CommentedConfigurationNode
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.function.Consumer
import kotlin.math.min

private const val FENCED_BLOCK = "```"

class PasteLongMessages(val bridge: Bridge, config: CommentedConfigurationNode) : Mutator {
    private val pasteService = PasteService()

    private var maxNewlines: Int = 4
    private var maxMsgLength: Int = 450
    private var pasteExpiryDays: Long = 7

    init {
        val msgNewlineCount = config.node("max-new-lines")
        if (msgNewlineCount.virtual()) {
            msgNewlineCount.set(maxNewlines)
        }

        val msgLengthNode = config.node("max-message-length")
        if (msgLengthNode.virtual()) {
            msgLengthNode.set(maxMsgLength)
        }

        val pasteExpiryNode = config.node("paste-expiration-in-days")
        if (pasteExpiryNode.virtual()) {
            pasteExpiryNode.comment("Number of days before paste expires. Use 0 to never expire.")
            pasteExpiryNode.set(pasteExpiryDays)
        }

        maxNewlines = msgNewlineCount.int
        maxMsgLength = msgLengthNode.int
        pasteExpiryDays = pasteExpiryNode.long
    }

    override fun mutate(message: PlatformMessage): Mutator.LifeCycle {
        val msgContents = message.contents

        // we only need to run paste service on discord messages
        if (message.source !is DiscordSource) {
            return Mutator.LifeCycle.CONTINUE
        }

        var shouldPaste = false

        if (countSubstring(msgContents, "\n") > maxNewlines) {
            shouldPaste = true
        }

        if (countSubstring(msgContents, FENCED_BLOCK) >= 2) {
            shouldPaste = true
        }

        if (msgContents.length > maxMsgLength) {
            shouldPaste = true
        }

        // if after all of that we don't meet the criteria
        // don't paste, return early
        if (!shouldPaste) {
            return Mutator.LifeCycle.CONTINUE
        }

        // called when we were unable to submit the paste
        val onError = Consumer<PasteService.Response> {
            resubmitToBridge(message)
        }

        // called when we were able to submit the paste
        val onSuccess = Consumer<PasteService.Response> {
            // just in case
            if (it.type != PasteService.Response.Type.SUCCESS || it.pasteUrl == null) {
                resubmitToBridge(message)
                return@Consumer
            }

            val cleaned = msgContents
                .replace("\n", " ")
                .replace("```", "")
                .replace("`", "")

            val maxLength = min(100, cleaned.length)
            val shortened = cleaned.substring(0, maxLength) + "..."

            message.contents = "$shortened${IrcFormattingCodes.RESET} ${it.pasteUrl}"
            resubmitToBridge(message)
        }

        // try and clean it up a bit before submitting
        // maybe figure out the highlight language
        var highlightLang: String? = null

        val builder = StringBuilder()
        val lines = msgContents.split("\n")
        for ((i, rawLine) in lines.withIndex()) {
            var line = rawLine

            if (i == 0 || i == lines.size - 1) {
                line = line.replace("```", "")

                // use whatever is left as the highlight lang
                // only if the length of the line is less than 10
                if (highlightLang == null && line.trim().isNotEmpty() && line.length < 10) {
                    highlightLang = line
                    line = "" // get rid of language str
                }

                // if we made the first or last line empty, just skip it
                if (line.trim() == "") {
                    continue
                }
            }

            builder.append(line).append("\n")
        }

        // call out to paste service for response at some unknown point in the future
        pasteService.dispatchPaste(builder.toString(), highlightLang, onSuccess, onError, pasteExpiryDays)

        // message will be resubmitted in the future with mutated content string
        // we want this version to die here
        return Mutator.LifeCycle.STOP_AND_DISCARD
    }

    /**
     * Resubmits the message to the bridge. Useful for async ops.
     */
    private fun resubmitToBridge(message: PlatformMessage) {
        bridge.logger.debug("Resubmitting to bridge: {}", message)
        val bridgeMsg = BridgeMessage(message)
        bridgeMsg.markMutatorApplied(this.javaClass)
        bridge.submitMessage(bridgeMsg)
    }

    /**
     * Paste service handler using https://paste.gg/
     */
    private class PasteService {
        private val httpClient = OkHttpClient()
        private val logger = LoggerFactory.getLogger("PasteService")

        /**
         * Dispatches a paste asynchronously to the paste service
         *
         * @param message contents to paste
         * @param highlightLang language name to use for syntax highlighting
         * @param successConsumer consumer to run if the paste is submitted successfully
         * @param errorConsumer consumer to run if there is any problem submitting the paste
         * @param expiryDays number of days before the paste expires, use 0 to never expire
         */
        fun dispatchPaste(
            message: String,
            highlightLang: String?,
            successConsumer: Consumer<Response>,
            errorConsumer: Consumer<Response>,
            expiryDays: Long
        ) {
            fun OffsetDateTime.toIso8601(): String = this.format(DateTimeFormatter.ISO_DATE_TIME)
            val rightNow = OffsetDateTime.now()

            // paste options
            val pasteName = rightNow.toIso8601()
            val visibility = PasteVisibility.UNLISTED.toString()
            val format = PasteFormat.TEXT.toString()

            // the paste service will barf if you send it an unsupported highlight language
            // so we must validate it before we use it
            var validatedLang: String? = null
            if (highlightLang != null && SUPPORTED_HIGHLIGHT_LANGS.contains(highlightLang.lowercase(Locale.ENGLISH))) {
                validatedLang = highlightLang.lowercase(Locale.ENGLISH)
            }

            // https://github.com/jkcclemens/paste/blob/b05ad0f468afa46170e46e2a73a2bd2ffec93db2/api.md#post-pastes
            val jsonPayload = JSONObject()
                .put("name", pasteName)
                .put("visibility", visibility)
                .put(
                    "files", JSONArray(
                        arrayOf(
                            JSONObject()
                                .put("name", "paste1")
                                .put("highlight_language", validatedLang)
                                .put(
                                    "content", JSONObject()
                                        .put("format", format)
                                        .put("value", message)
                                )
                        )
                    )
                )

            if (expiryDays > 0) {
                jsonPayload.put("expires", rightNow.plusDays(expiryDays).toIso8601())
            }

            logger.debug("JSON payload")
            logJson(jsonPayload)

            val request = Request.Builder()
                .header("Content-Type", "application/json")
                .url(PASTE_SERVICE_POST_URL)
                .post(jsonPayload.toString(2).toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: okhttp3.Response) {
                    val rawResp = response.body?.string()
                    val respJson: JSONObject? = try {
                        JSONObject(rawResp)
                    } catch (ignored: JSONException) {
                        null
                    }

                    // log response regardless of result
                    logger.debug("JSON response")
                    if (respJson != null) {
                        logJson(respJson)
                    } else {
                        logger.warn("Unable to parse response body as JSON!")
                        logger.debug("Raw: $rawResp")
                    }

                    if (!response.isSuccessful || respJson == null) {
                        logger.debug("Received HTTP response FAILURE")

                        val failureResponse = Response(Response.Type.FAILURE, null, respJson, rawResp)
                        errorConsumer.accept(failureResponse)
                    } else {
                        logger.debug("Received HTTP response SUCCESS")

                        val pasteId = respJson.getJSONObject("result").getString("id")
                        val deletionKey = respJson.getJSONObject("result").getString("deletion_key")
                        val pasteUrl = PASTE_SERVICE_OUT_BASE_URL + pasteId
                        val response = Response(Response.Type.SUCCESS, pasteUrl, respJson, rawResp)
                        logger.info("Deletion key for $pasteName is: $deletionKey")
                        successConsumer.accept(response)
                    }
                }

                override fun onFailure(call: Call, ex: IOException) {
                    logger.error("Unable to make outgoing call to paste service! $ex")
                    ex.printStackTrace()

                    errorConsumer.accept(Response(Response.Type.FAILURE, null, null, null))
                }

            })
        }

        private fun logJson(json: JSONObject) {
            for ((i, line) in json.toString(4).split("\n").withIndex()) {
                logger.debug("$i  $line")
            }
        }

        private enum class PasteVisibility(private val literal: String) {
            PUBLIC("public"),
            UNLISTED("unlisted"),
            PRIVATE("private");

            override fun toString(): String {
                return literal
            }
        }

        private enum class PasteFormat(private val literal: String) {
            TEXT("text"),
            BASE64("base64"),
            GZIP("gzip"),
            XZ("xz");

            override fun toString(): String {
                return literal
            }
        }

        data class Response(
            val type: Type,
            val pasteUrl: String?,
            val body: JSONObject?,
            val rawBody: String?
        ) {
            enum class Type {
                SUCCESS,
                FAILURE
            }
        }
    }
}

private const val PASTE_SERVICE_POST_URL = "https://api.paste.gg/v1/pastes/"
private const val PASTE_SERVICE_OUT_BASE_URL = "https://paste.gg/p/anonymous/"

/**
 * Constructs a new set of supported highlight languages
 *
 * This could be an enum or something but this isn't a public API and I'm never going
 * to use it as an enum.
 *
 * https://github.com/jkcclemens/paste/blob/942d1ede8abe80a594553197f2b03c1d6d70efd0/webserver/src/utils/language.rs
 */
private val SUPPORTED_HIGHLIGHT_LANGS = makeSupportedLangsSet()
private fun makeSupportedLangsSet(): HashSet<String> {
    val set = HashSet<String>()
    set.add("onec")
    set.add("abnf")
    set.add("accesslog")
    set.add("actionscript")
    set.add("ada")
    set.add("apache")
    set.add("applescript")
    set.add("arduino")
    set.add("armasm")
    set.add("asciidoc")
    set.add("aspectj")
    set.add("autohotkey")
    set.add("autoit")
    set.add("avrasm")
    set.add("awk")
    set.add("axapta")
    set.add("bash")
    set.add("basic")
    set.add("bnf")
    set.add("brainfuck")
    set.add("cal")
    set.add("capnproto")
    set.add("ceylon")
    set.add("clean")
    set.add("clojure")
    set.add("clojurerepl")
    set.add("cmake")
    set.add("coffeescript")
    set.add("coq")
    set.add("cos")
    set.add("cplusplus")
    set.add("crmsh")
    set.add("crystal")
    set.add("csharp")
    set.add("csp")
    set.add("css")
    set.add("d")
    set.add("dart")
    set.add("delphi")
    set.add("diff")
    set.add("django")
    set.add("dns")
    set.add("dockerfile")
    set.add("dos")
    set.add("dsconfig")
    set.add("dts")
    set.add("dust")
    set.add("ebnf")
    set.add("elixir")
    set.add("elm")
    set.add("embeddedruby")
    set.add("erlang")
    set.add("erlangrepl")
    set.add("excel")
    set.add("fix")
    set.add("flix")
    set.add("fortran")
    set.add("fsharp")
    set.add("gams")
    set.add("gauss")
    set.add("gcode")
    set.add("gherkin")
    set.add("glsl")
    set.add("go")
    set.add("golo")
    set.add("gradle")
    set.add("groovy")
    set.add("haml")
    set.add("handlebars")
    set.add("haskell")
    set.add("haxe")
    set.add("hsp")
    set.add("htmlbars")
    set.add("http")
    set.add("hy")
    set.add("inform7")
    set.add("ini")
    set.add("irpf90")
    set.add("java")
    set.add("javascript")
    set.add("jbosscli")
    set.add("json")
    set.add("julia")
    set.add("juliarepl")
    set.add("kotlin")
    set.add("lasso")
    set.add("ldif")
    set.add("leaf")
    set.add("less")
    set.add("lisp")
    set.add("livecodeserver")
    set.add("livescript")
    set.add("llvm")
    set.add("lindenscriptinglanguage")
    set.add("lua")
    set.add("makefile")
    set.add("markdown")
    set.add("mathematica")
    set.add("matlab")
    set.add("maxima")
    set.add("mel")
    set.add("mercury")
    set.add("mipsasm")
    set.add("mizar")
    set.add("mojolicious")
    set.add("monkey")
    set.add("moonscript")
    set.add("n1ql")
    set.add("nginx")
    set.add("nimrod")
    set.add("nix")
    set.add("nsis")
    set.add("objectivec")
    set.add("ocaml")
    set.add("openscad")
    set.add("oxygene")
    set.add("parser3")
    set.add("perl")
    set.add("pf")
    set.add("php")
    set.add("pony")
    set.add("powershell")
    set.add("processing")
    set.add("profile")
    set.add("prolog")
    set.add("protocolbuffers")
    set.add("puppet")
    set.add("purebasic")
    set.add("python")
    set.add("q")
    set.add("qml")
    set.add("r")
    set.add("rib")
    set.add("roboconf")
    set.add("routeros")
    set.add("rsl")
    set.add("ruby")
    set.add("ruleslanguage")
    set.add("rust")
    set.add("scala")
    set.add("scheme")
    set.add("scilab")
    set.add("scss")
    set.add("shell")
    set.add("smali")
    set.add("smalltalk")
    set.add("standardml")
    set.add("sqf")
    set.add("sql")
    set.add("stan")
    set.add("stata")
    set.add("step21")
    set.add("stylus")
    set.add("subunit")
    set.add("swift")
    set.add("taggerscript")
    set.add("tap")
    set.add("tcl")
    set.add("tex")
    set.add("thrift")
    set.add("tp")
    set.add("twig")
    set.add("typescript")
    set.add("vala")
    set.add("vbnet")
    set.add("vbscript")
    set.add("vbscripthtml")
    set.add("verilog")
    set.add("vhdl")
    set.add("vim")
    set.add("x86asm")
    set.add("xl")
    set.add("xml")
    set.add("xquery")
    set.add("yaml")
    set.add("zephir")

    return set
}
