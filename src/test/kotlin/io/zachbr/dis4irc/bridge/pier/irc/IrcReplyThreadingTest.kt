/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.TEST_DISCORD_CHANNEL_ID
import io.zachbr.dis4irc.bridge.TEST_IRC_CHANNEL
import io.zachbr.dis4irc.bridge.testBridge
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.DiscordSender
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.IrcMessage
import io.zachbr.dis4irc.bridge.message.IrcSender
import io.zachbr.dis4irc.bridge.message.IrcSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime

private const val CHANNEL = TEST_IRC_CHANNEL
private const val DISCORD_CHANNEL_ID = TEST_DISCORD_CHANNEL_ID
private const val REPLY_TAG = "+draft/reply"

/**
 * Runs the real IRC pier against [FakeIrcServer] to check Discord replies go out as IRCv3 threaded replies.
 */
class IrcReplyThreadingTest {
    private val resources = ArrayList<AutoCloseable>()

    @AfterEach
    fun cleanup() {
        resources.reversed().forEach { runCatching { it.close() } }
    }

    private fun startBridge(
        server: FakeIrcServer,
        replyTags: Boolean = true,
        contextWithTags: Boolean? = null
    ): Bridge {
        val bridge = testBridge(server.port) {
            it.node("irc", "discord-reply-tags").set(replyTags)
            contextWithTags?.let { value -> it.node("irc", "discord-reply-context-with-tags").set(value) }
        }
        bridge.ircConn.start()
        resources.add(AutoCloseable { bridge.ircConn.onShutdown() })
        assertTrue(server.awaitJoin(CHANNEL), "bridge never joined $CHANNEL")
        return bridge
    }

    private fun server(capabilities: List<String> = listOf("message-tags", "echo-message"), clientTagDeny: String? = null): FakeIrcServer {
        return FakeIrcServer(capabilities, clientTagDeny).also { resources.add(it) }
    }

    private fun discordMessage(id: Long, text: String, replyTo: DiscordMessage? = null) = DiscordMessage(
        text,
        DiscordSender("WhaTheSht", 5L),
        DiscordSource("bridged", DISCORD_CHANNEL_ID),
        Instant.now(),
        sentTimestamp = OffsetDateTime.now(),
        referencedMessage = replyTo,
        messageId = id
    )

    private fun Bridge.send(message: DiscordMessage) = ircConn.sendMessage(CHANNEL, BridgeMessage(message))

    private fun Bridge.awaitIrcMsgId(discordMessageId: Long, timeoutMillis: Long = 10_000): String? {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            messageIds.getIrcMsgId(discordMessageId)?.let { return it }
            Thread.sleep(25)
        }
        return null
    }

    @Test
    fun discordReplyToDiscordMessageIsThreaded() {
        val server = server()
        val bridge = startBridge(server, contextWithTags = true)

        val original = discordMessage(1, "kick attempt is dumb")
        bridge.send(original)
        val originalMsgId = bridge.awaitIrcMsgId(1)
        assertNotNull(originalMsgId, "echo of the original was not recorded")
        assertEquals(server.sent.single().msgId, originalMsgId)

        bridge.send(discordMessage(2, "hilarious hearing this from a pats fan", original))
        val (context, reply) = server.awaitSent(3).drop(1)

        assertEquals("Reply to \"WhaTheSht: kick attempt is dumb\"", context.text)
        assertEquals("<WhaTheSht> hilarious hearing this from a pats fan", reply.text)
        assertEquals(originalMsgId, context.clientTags[REPLY_TAG])
        assertEquals(originalMsgId, reply.clientTags[REPLY_TAG])
    }

    @Test
    fun discordReplyToIrcMessageIsThreaded() {
        val server = server()
        val bridge = startBridge(server)

        // an IRC message that the Discord pier then posted as Discord message 50
        val ircMsgId = server.sendFromUser("skud", CHANNEL, "lmao broadcast camera follows the fake")
        val ircMessage = IrcMessage("lmao broadcast camera follows the fake", IrcSender("skud", null), IrcSource(CHANNEL), Instant.now(), ircMsgId)
        bridge.discordConn.recordBridgedMessage(ircMessage, 50)

        val bridgedCopy = discordMessage(50, "lmao broadcast camera follows the fake")
        bridge.send(discordMessage(51, "roasted", bridgedCopy))

        val reply = server.awaitSent(1).single()
        assertEquals("<WhaTheSht> roasted", reply.text)
        assertEquals(ircMsgId, reply.clientTags[REPLY_TAG])
    }

    @Test
    fun contextLineIsDroppedWhenThreadedByDefault() {
        val server = server()
        val bridge = startBridge(server)

        val original = discordMessage(1, "first")
        bridge.send(original)
        val originalMsgId = bridge.awaitIrcMsgId(1)

        bridge.send(discordMessage(2, "reply to first", original))
        val sent = server.awaitSent(2)
        Thread.sleep(1500) // make sure no extra line follows

        assertEquals(2, server.sent.size)
        assertEquals("<WhaTheSht> reply to first", sent[1].text)
        assertEquals(originalMsgId, sent[1].clientTags[REPLY_TAG])
    }

    @Test
    fun replyToUnknownMessageFallsBackToContext() {
        val server = server()
        val bridge = startBridge(server, contextWithTags = false)

        bridge.send(discordMessage(2, "replying to something old", discordMessage(999, "old message")))
        val sent = server.awaitSent(2)

        assertEquals("Reply to \"WhaTheSht: old message\"", sent[0].text)
        assertEquals("<WhaTheSht> replying to something old", sent[1].text)
        assertTrue(sent.none { REPLY_TAG in it.clientTags })
    }

    @Test
    fun longRepliesAreTaggedOnEveryPart() {
        val server = server()
        val bridge = startBridge(server, contextWithTags = false)

        val original = discordMessage(1, "first")
        bridge.send(original)
        val originalMsgId = bridge.awaitIrcMsgId(1)

        bridge.send(discordMessage(2, "long ".repeat(150).trim(), original))
        val parts = server.awaitSent(3).drop(1)

        assertTrue(parts.size >= 2, "expected the long reply to be split")
        assertTrue(parts.all { it.clientTags[REPLY_TAG] == originalMsgId })
        assertEquals("<WhaTheSht> " + "long ".repeat(150).trim(), parts.joinToString(" ") { it.text })
    }

    @Test
    fun noTagsWithoutMessageTagsCapability() {
        val server = server(capabilities = listOf("echo-message"))
        val bridge = startBridge(server)

        bridge.messageIds.put(1, "known")
        bridge.send(discordMessage(2, "reply", discordMessage(1, "first")))
        val sent = server.awaitSent(2)

        assertEquals("Reply to \"WhaTheSht: first\"", sent[0].text)
        assertTrue(sent.none { it.clientTags.isNotEmpty() })
    }

    @Test
    fun nothingRecordedWithoutEchoMessage() {
        val server = server(capabilities = listOf("message-tags"))
        val bridge = startBridge(server)

        bridge.send(discordMessage(1, "first"))
        server.awaitSent(1)

        assertNull(bridge.awaitIrcMsgId(1, timeoutMillis = 1500))
    }

    @Test
    fun clientTagDenyIsRespected() {
        val server = server(clientTagDeny = "*")
        val bridge = startBridge(server)

        bridge.messageIds.put(1, "known")
        bridge.send(discordMessage(2, "reply", discordMessage(1, "first")))

        assertTrue(server.awaitSent(2).none { REPLY_TAG in it.clientTags })
    }

    @Test
    fun clientTagDenyExceptionAllowsReplies() {
        // what UnrealIRCd 6 advertises by default
        val server = server(clientTagDeny = "*,-draft/typing,-typing,-draft/reply,-reply")
        val bridge = startBridge(server, contextWithTags = false)

        bridge.messageIds.put(1, "known")
        bridge.send(discordMessage(2, "reply", discordMessage(1, "first")))

        assertEquals("known", server.awaitSent(1).single().clientTags[REPLY_TAG])
    }

    @Test
    fun disabledReplyTagsKeepsPreviousBehaviour() {
        val server = server()
        val bridge = startBridge(server, replyTags = false)

        bridge.send(discordMessage(1, "first"))
        bridge.send(discordMessage(2, "reply", discordMessage(1, "first")))
        val sent = server.awaitSent(3)

        assertEquals(listOf("<WhaTheSht> first", "Reply to \"WhaTheSht: first\"", "<WhaTheSht> reply"), sent.map { it.text })
        assertTrue(sent.none { it.clientTags.isNotEmpty() })
        assertNull(bridge.messageIds.getIrcMsgId(1), "echo-message should not have been requested")
    }
}
