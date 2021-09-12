/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageTest {
    @Test
    fun testShouldSendTo() {
        val sender = Sender("SomeSender", "SomeSender#1234", null, null)

        // test from IRC
        val ircSource = Source("#some-channel", null, PlatformType.IRC)
        val ircMessage = Message("Test", sender, ircSource, System.currentTimeMillis())

        ircMessage.destination = Destination.DISCORD
        assertFalse(ircMessage.shouldSendTo(PlatformType.IRC))
        assertTrue(ircMessage.shouldSendTo(PlatformType.DISCORD))

        ircMessage.destination = Destination.IRC
        assertTrue(ircMessage.shouldSendTo(PlatformType.IRC))
        assertFalse(ircMessage.shouldSendTo(PlatformType.DISCORD))

        ircMessage.destination = Destination.ORIGIN
        assertTrue(ircMessage.shouldSendTo(PlatformType.IRC))
        assertFalse(ircMessage.shouldSendTo(PlatformType.DISCORD))

        ircMessage.destination = Destination.OPPOSITE
        assertFalse(ircMessage.shouldSendTo(PlatformType.IRC))
        assertTrue(ircMessage.shouldSendTo(PlatformType.DISCORD))

        ircMessage.destination = Destination.BOTH
        assertTrue(ircMessage.shouldSendTo(PlatformType.IRC))
        assertTrue(ircMessage.shouldSendTo(PlatformType.DISCORD))

        // test from Discord
        val discordSource = Source("some-channel", 1L, PlatformType.DISCORD)
        val discordMessage = Message("Test", sender, discordSource, System.currentTimeMillis())

        discordMessage.destination = Destination.DISCORD
        assertFalse(discordMessage.shouldSendTo(PlatformType.IRC))
        assertTrue(discordMessage.shouldSendTo(PlatformType.DISCORD))

        discordMessage.destination = Destination.IRC
        assertTrue(discordMessage.shouldSendTo(PlatformType.IRC))
        assertFalse(discordMessage.shouldSendTo(PlatformType.DISCORD))

        discordMessage.destination = Destination.ORIGIN
        assertFalse(discordMessage.shouldSendTo(PlatformType.IRC))
        assertTrue(discordMessage.shouldSendTo(PlatformType.DISCORD))

        discordMessage.destination = Destination.OPPOSITE
        assertTrue(discordMessage.shouldSendTo(PlatformType.IRC))
        assertFalse(discordMessage.shouldSendTo(PlatformType.DISCORD))

        discordMessage.destination = Destination.BOTH
        assertTrue(discordMessage.shouldSendTo(PlatformType.IRC))
        assertTrue(discordMessage.shouldSendTo(PlatformType.DISCORD))
    }
}
