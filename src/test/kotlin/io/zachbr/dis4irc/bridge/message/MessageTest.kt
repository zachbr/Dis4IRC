/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MessageTest {
    @Test
    fun testShouldSendTo() {
        // test from IRC
        val ircSender = IrcSender("SomeSender", null)
        val ircSource = IrcSource("#some-channel")
        val ircMessage = IrcMessage("Test", ircSender, ircSource, Instant.now())
        val ircBridgeMsg = BridgeMessage(ircMessage)

        ircBridgeMsg.destination = Destination.DISCORD
        assertFalse(ircBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertTrue(ircBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        ircBridgeMsg.destination = Destination.IRC
        assertTrue(ircBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertFalse(ircBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        ircBridgeMsg.destination = Destination.ORIGIN
        assertTrue(ircBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertFalse(ircBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        ircBridgeMsg.destination = Destination.OPPOSITE
        assertFalse(ircBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertTrue(ircBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        ircBridgeMsg.destination = Destination.BOTH
        assertTrue(ircBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertTrue(ircBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        // test from Discord
        val discordSource = DiscordSource("some-channel", 1L)
        val discordSender = DiscordSender("SomeSender", 0L)
        val discordMessage = DiscordMessage("Test", discordSender, discordSource, Instant.now())
        val discordBridgeMsg = BridgeMessage(discordMessage)

        discordBridgeMsg.destination = Destination.DISCORD
        assertFalse(discordBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertTrue(discordBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        discordBridgeMsg.destination = Destination.IRC
        assertTrue(discordBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertFalse(discordBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        discordBridgeMsg.destination = Destination.ORIGIN
        assertFalse(discordBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertTrue(discordBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        discordBridgeMsg.destination = Destination.OPPOSITE
        assertTrue(discordBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertFalse(discordBridgeMsg.shouldSendTo(PlatformType.DISCORD))

        discordBridgeMsg.destination = Destination.BOTH
        assertTrue(discordBridgeMsg.shouldSendTo(PlatformType.IRC))
        assertTrue(discordBridgeMsg.shouldSendTo(PlatformType.DISCORD))
    }
}
