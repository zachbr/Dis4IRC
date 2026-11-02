/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

private const val SERVER_NAME = "fake.irc"
private const val USER_HOST = "bridge@bridge.host"

/**
 * A minimal single-client IRCv3 server, just enough for the bridge's IRC pier to connect, join and talk.
 *
 * Messages are not relayed between users. Instead the server records every PRIVMSG the bridge sends,
 * assigns it a msgid, and echoes it back when echo-message is enabled, so tests can check what went out on the wire.
 */
class FakeIrcServer(
    private val capabilities: List<String> = listOf("message-tags", "echo-message"),
    private val clientTagDeny: String? = null
) : AutoCloseable {
    data class SentMessage(val clientTags: Map<String, String>, val target: String, val text: String, val msgId: String)

    private val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
    private val msgIdCounter = AtomicInteger()
    private val joined = HashMap<String, CountDownLatch>()
    private val enabledCaps: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var client: Socket? = null
    @Volatile private var writer: OutputStreamWriter? = null
    @Volatile private var nick = "*"
    private var gotUser = false
    private var capNegotiating = false
    private var registered = false

    val port: Int get() = serverSocket.localPort
    val sent = CopyOnWriteArrayList<SentMessage>()

    init {
        thread(isDaemon = true, name = "fake-irc-accept") {
            val socket = runCatching { serverSocket.accept() }.getOrNull() ?: return@thread
            client = socket
            writer = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            while (true) {
                val line = runCatching { reader.readLine() }.getOrNull() ?: break
                handle(line)
            }
        }
    }

    /**
     * Waits for the bridge to join the channel and be ready to send to it
     */
    fun awaitJoin(channel: String, timeoutSeconds: Long = 10): Boolean = latchFor(channel).await(timeoutSeconds, TimeUnit.SECONDS)

    /**
     * Waits for the bridge to have sent at least the given number of messages
     */
    fun awaitSent(count: Int, timeoutMillis: Long = 10_000): List<SentMessage> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (sent.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        return sent.toList()
    }

    /**
     * Sends a channel message to the bridge as if from another user, returning its msgid
     */
    fun sendFromUser(fromNick: String, channel: String, text: String): String {
        val msgId = nextMsgId()
        val tags = if ("message-tags" in enabledCaps) "@msgid=$msgId " else ""
        write("$tags:$fromNick!user@user.host PRIVMSG $channel :$text")
        return msgId
    }

    private fun handle(rawLine: String) {
        var line = rawLine
        var clientTags = emptyMap<String, String>()
        if (line.startsWith("@")) {
            val space = line.indexOf(' ')
            clientTags = parseTags(line.substring(1, space))
            line = line.substring(space + 1)
        }

        val (command, params) = parse(line)
        when (command) {
            "CAP" -> handleCap(params)
            "NICK" -> {
                nick = params[0]
                tryRegister()
            }
            "USER" -> {
                gotUser = true
                tryRegister()
            }
            "PING" -> write(":$SERVER_NAME PONG $SERVER_NAME :${params.getOrElse(0) { "" }}")
            "WHOIS" -> {
                write(":$SERVER_NAME 311 $nick $nick bridge bridge.host * :Bridge")
                write(":$SERVER_NAME 318 $nick $nick :End of /WHOIS list.")
            }
            "JOIN" -> for (channel in params[0].split(',')) {
                write(":$nick!$USER_HOST JOIN $channel")
                write(":$SERVER_NAME 353 $nick = $channel :$nick")
                write(":$SERVER_NAME 366 $nick $channel :End of /NAMES list.")
            }
            "WHO" -> {
                val channel = params.getOrElse(0) { "*" }
                write(":$SERVER_NAME 315 $nick $channel :End of /WHO list.")
                // the client sends WHO once it is tracking the channel, so it can now send to it
                latchFor(channel).countDown()
            }
            "PRIVMSG" -> {
                val msgId = nextMsgId()
                val target = params[0]
                val text = params[1]
                sent.add(SentMessage(clientTags, target, text, msgId))
                if ("echo-message" in enabledCaps) {
                    val echoTags = (listOf("msgid=$msgId") + clientTags.map { "${it.key}=${it.value}" }).joinToString(";")
                    write("@$echoTags :$nick!$USER_HOST PRIVMSG $target :$text")
                }
            }
        }
    }

    /**
     * Completes registration once NICK and USER are in and any CAP negotiation has ended, as a real server does
     */
    private fun tryRegister() {
        if (registered || nick == "*" || !gotUser || capNegotiating) {
            return
        }

        registered = true
        write(":$SERVER_NAME 001 $nick :Welcome")
        write(":$SERVER_NAME 002 $nick :Your host is $SERVER_NAME")
        write(":$SERVER_NAME 003 $nick :This server was created today")
        write(":$SERVER_NAME 004 $nick $SERVER_NAME fake-1.0 iowrs biklmnopstv")
        val isupport = listOfNotNull("NETWORK=Fake", clientTagDeny?.let { "CLIENTTAGDENY=$it" }).joinToString(" ")
        write(":$SERVER_NAME 005 $nick $isupport :are supported by this server")
        write(":$SERVER_NAME 375 $nick :- $SERVER_NAME Message of the day -")
        write(":$SERVER_NAME 372 $nick :- hello")
        write(":$SERVER_NAME 376 $nick :End of /MOTD command.")
    }

    private fun handleCap(params: List<String>) {
        when (params[0]) {
            "END" -> {
                capNegotiating = false
                tryRegister()
            }
            "LS" -> capNegotiating = true
        }
        when (params[0]) {
            "LS" -> write(":$SERVER_NAME CAP * LS :${capabilities.joinToString(" ")}")
            "REQ" -> {
                val requested = params[1].split(' ').filter { it.isNotBlank() }
                if (requested.all { it in capabilities }) {
                    enabledCaps.addAll(requested)
                    write(":$SERVER_NAME CAP $nick ACK :${requested.joinToString(" ")}")
                } else {
                    write(":$SERVER_NAME CAP $nick NAK :${requested.joinToString(" ")}")
                }
            }
        }
    }

    private fun parse(line: String): Pair<String, List<String>> {
        val trailingIndex = line.indexOf(" :")
        val head = if (trailingIndex == -1) line else line.substring(0, trailingIndex)
        val parts = head.split(' ').filter { it.isNotEmpty() }.toMutableList()
        if (trailingIndex != -1) {
            parts.add(line.substring(trailingIndex + 2))
        }
        return parts.first().uppercase() to parts.drop(1)
    }

    private fun parseTags(tags: String): Map<String, String> = tags.split(';').associate {
        val eq = it.indexOf('=')
        if (eq == -1) it to "" else it.substring(0, eq) to it.substring(eq + 1)
    }

    private fun nextMsgId(): String = "msgid${msgIdCounter.incrementAndGet()}"

    @Synchronized
    private fun latchFor(channel: String): CountDownLatch = joined.getOrPut(channel.lowercase()) { CountDownLatch(1) }

    @Synchronized
    private fun write(line: String) {
        val out = writer ?: return
        runCatching {
            out.write("$line\r\n")
            out.flush()
        }
    }

    override fun close() {
        runCatching { client?.close() }
        runCatching { serverSocket.close() }
    }
}
