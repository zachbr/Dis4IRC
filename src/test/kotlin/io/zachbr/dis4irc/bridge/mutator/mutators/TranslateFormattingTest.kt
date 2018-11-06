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

import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.message.Source
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.kitteh.irc.client.library.util.Format

class TranslateFormattingTest {
    @Test
    fun testIt() {
        this.testIrcToDiscord("[Test] test pushed **1** new commit to master: __https://example.com/example__", "[\u000313Test\u000F] \u000315test\u000F pushed \u00021\u000F new commit to \u000306master\u000F: \u000302\u001Fhttps://example.com/example\u000F")
        this.testIrcToDiscord("abc123", Format.RED.toString() + "abc123")
        this.testIrcToDiscord("***This is a*** test.", Format.BOLD.toString() + Format.ITALIC.toString() + "This is a" + Format.RESET.toString() + " test.")
        this.testIrcToDiscord("kitten**bold**", Format.MAGENTA.toString() + "kitten" + Format.BOLD.toString() + "bold")
        this.testIrcToDiscord("**boldkitten**unbold", Format.BOLD.toString() + "bold" + Format.MAGENTA.toString() + "kitten" + Format.BOLD.toString() + "unbold")
        this.testIrcToDiscord("destroy", "\u000311,08d\u000302,07e\u000312,06s\u000306,07t\u000313,02r\u000305,09o\u000304,07y\u000F")
        this.testIrcToDiscord("d1e2s3t4r5o6y7", "\u000307,11d\u000308,101\u000303,11e\u000309,132\u000310,07s\u000311,093\u000302,09t\u000312,084\u000306,03r\u000313,115\u000305,12o\u000304,136\u000307,13y\u000308,027\u000F")
    }

    private fun testIrcToDiscord(expected: String, string: String) {
        val message = Message(string, Sender("Test", null, null), Source("#test", null, PlatformType.IRC), System.nanoTime())
        val mutator = TranslateFormatting()
        mutator.mutate(message)
        assertEquals(expected, message.contents)
    }
}
