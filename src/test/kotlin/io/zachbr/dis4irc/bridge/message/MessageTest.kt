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

package io.zachbr.dis4irc.bridge.message

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class MessageTest {
    @Test
    fun testShouldSendTo() {
        val sender = Sender("SomeSender", null, null)

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
