/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.TEST_IRC_CHANNEL
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.DiscordSender
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.IrcMessage
import io.zachbr.dis4irc.bridge.message.IrcSender
import io.zachbr.dis4irc.bridge.message.IrcSource
import io.zachbr.dis4irc.bridge.testBridge
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.OffsetDateTime

class DiscordReplyTest {
    private fun ircMessage(msgId: String?, replyTo: String? = null) =
        IrcMessage("text", IrcSender("RustyCloud", null), IrcSource(TEST_IRC_CHANNEL), Instant.now(), msgId, replyTo)

    @Test
    fun ircReplyToBridgedMessageTargetsItsDiscordCopy() {
        val bridge = testBridge()
        // IRC message msgidA became Discord message 50
        bridge.discordConn.recordBridgedMessage(ircMessage("msgidA"), 50)
        // Discord message 60 went to IRC as msgidB
        bridge.messageIds.put(60, "msgidB")

        assertEquals(50L, bridge.discordConn.getReplyTarget(ircMessage("msgidC", replyTo = "msgidA")))
        assertEquals(60L, bridge.discordConn.getReplyTarget(ircMessage("msgidD", replyTo = "msgidB")))
    }

    @Test
    fun noReplyTargetWhenUnknownOrNotAReply() {
        val bridge = testBridge()
        bridge.messageIds.put(60, "msgidB")

        assertNull(bridge.discordConn.getReplyTarget(ircMessage("msgidC", replyTo = "unknown")))
        assertNull(bridge.discordConn.getReplyTarget(ircMessage("msgidC")))
        assertNull(bridge.discordConn.getReplyTarget(ircMessage(null)))
        val discordMessage = DiscordMessage("text", DiscordSender("a", 1L), DiscordSource("c", 2L), Instant.now(), sentTimestamp = OffsetDateTime.now())
        assertNull(bridge.discordConn.getReplyTarget(discordMessage))
    }

    @Test
    fun threadingIrcRepliesCanBeDisabled() {
        val bridge = testBridge { it.node("discord-options", "thread-irc-replies").set(false) }
        bridge.messageIds.put(60, "msgidB")

        assertNull(bridge.discordConn.getReplyTarget(ircMessage("msgidC", replyTo = "msgidB")))
    }

    @Test
    fun recordsOnlyIrcMessagesWithMsgIds() {
        val bridge = testBridge()
        bridge.discordConn.recordBridgedMessage(ircMessage(null), 70)
        assertNull(bridge.messageIds.getIrcMsgId(70))
    }
}
