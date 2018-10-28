/*
 * This file is part of Dis4IRC.
 *
 * Dis4IRC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dis4IRC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Dis4IRC.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import okhttp3.*
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.lang.StringBuilder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class PasteLongMessages : Mutator {
    private val pasteService = PasteService()

    private val maxNewlines = 4 // todo - config
    private val fencedBlock = "```"
    private val maxMsgLength = 450 // todo - config

    override fun mutate(message: String, source: Channel, sender: Sender, attachments: MutableList<String>?): String? {
        // we only need to run paste service on discord messages
        if (source.type != Channel.Type.DISCORD) {
            return message
        }

        var shouldPaste = false

        if (message.split("\n").size > maxNewlines) {
            shouldPaste = true
        }

        if (message.contains(fencedBlock)) {
            shouldPaste = true
        }

        if (message.length > maxMsgLength) {
            shouldPaste = true
        }

        // if after all of that we don't meet the criteria
        // don't paste, return early
        if (!shouldPaste) {
            return message
        }

        val response = pasteService.pasteSync(message)
        if (response.type == PasteService.Response.Type.SUCCESS) {
            val pasteUrl = response.pasteUrl ?: return message // return early if null pasteUrl

            // shorten text up and append paste url
            val builder = StringBuilder()

            val words = message.replace("\n", "").split(" ")
            for ((index, word) in words.withIndex()) {
                builder.append(word)

                if (index >= 5 || index == words.size) {
                    builder.append("...")
                    break
                }
            }

            var shortened = builder.toString()
            // just in case we somehow ended up with a really long string after all of that
            // cap its max length at 100 chars
            if (shortened.length > 100) {
                shortened = shortened.substring(0, 100)
            }

            return "$shortened $pasteUrl"
        } else {
            // just return the base string, nothing we can do
            return message
        }
    }

    /**
     * Paste service handler using https://paste.gg/
     */
    class PasteService {
        private val httpClient = OkHttpClient()
        private val logger = LoggerFactory.getLogger("PasteService")

        // blocking // todo - its blocking...
        internal fun pasteSync(message: String): Response {
            fun OffsetDateTime.toIso8601(): String = this.format(DateTimeFormatter.ISO_DATE_TIME)

            // sanitize a bit
            val sanitizer = StringBuilder()
            val lines = message.split("\n")
            for ((i, line) in lines.withIndex()) {
                if (i == 0 || i == lines.size - 1) {
                    line.replace("```", "")
                }

                if (line.trim() == "") {
                    continue
                }

                sanitizer.append(line).append("\n")
            }

            val pasteContents = sanitizer.toString()
            val rightNow = OffsetDateTime.now()

            // paste options
            val pasteName = rightNow.toIso8601()
            val visibility = PasteVisibility.UNLISTED.toString()
            val format = PasteFormat.TEXT.toString()
            val expires = rightNow.plusDays(7).toIso8601()

            // https://github.com/jkcclemens/paste/blob/b05ad0f468afa46170e46e2a73a2bd2ffec93db2/api.md#post-pastes
            val jsonPayload = JSONObject()
                .put("name", pasteName)
                .put("visibility", visibility)
                .put("expires", expires)
                .put("files", JSONArray(arrayOf(JSONObject()
                    .put("name", "paste1")
                    .put("content", JSONObject()
                        .put("format", format)
                        .put("value", pasteContents)
                    )
                )))

            logger.debug("JSON payload")
            logJson(jsonPayload)

            val request = Request.Builder()
                .header("Content-Type", "application/json")
                .url(PASTE_SERVICE_POST_URL)
                .post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonPayload.toString(2)))
                .build()

            val response = httpClient.newCall(request).execute()

            val rawResp = response.body()?.string()
            val respJson: JSONObject? = try {
                JSONObject(rawResp)
            } catch (ignored: JSONException) {
                null
            }

            // debug log response
            logger.debug("JSON response")
            if (respJson != null) {
                logJson(respJson)
            } else {
                logger.debug(rawResp)
            }

            if (!response.isSuccessful) {
                logger.debug("Received response FAILURE")

                return Response(Response.Type.FAILURE, null, respJson, rawResp)
            } else {
                logger.debug("Received response SUCCESS")

                // successful but invalid json? treat as failure
                if (respJson == null) {
                    logger.warn("Null JSON response from successful response!")
                    return Response(Response.Type.FAILURE, null, respJson, rawResp)
                }

                val pasteId = respJson.getJSONObject("result").getString("id")
                val pasteUrl = PASTE_SERVICE_OUT_BASE_URL + pasteId
                return Response(Response.Type.SUCCESS, pasteUrl, respJson, rawResp)
            }
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

        internal data class Response(
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

        companion object {
            private const val PASTE_SERVICE_POST_URL = "https://api.paste.gg/v1/pastes"
            private const val PASTE_SERVICE_OUT_BASE_URL = "https://paste.gg/p/anonymous/"
        }
    }
}
