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
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.util.countSubstring
import java.util.*

// I haven't seen it be an issue but the back of my head says it could be, so remove dashes from this key
private val UNIQUE_KEY_STR = UUID.randomUUID().toString().replace("-", "")

/**
 * Translates Discord's markdown formatting to the IRC formatting codes and vice versa
 */
class TranslateFormatting : Mutator {
    override fun mutate(message: Message): Mutator.LifeCycle {
        message.contents = when (message.source.type) {
            PlatformType.IRC -> formatForDiscord(message.contents)
            PlatformType.DISCORD -> formatForIrc(message.contents)
        }

        return Mutator.LifeCycle.CONTINUE
    }

    /**
     * Takes a message from IRC and translates the formatting to Discord compatible rendering chars
     */
    private fun formatForDiscord(message: String): String {
        var out = fixFormattingBalance(message, IrcFormattingCodes.values())

        out = out.replace(IrcFormattingCodes.BOLD.code, DiscordFormattingCodes.BOLD.code)
        out = out.replace(IrcFormattingCodes.ITALICS.code, DiscordFormattingCodes.ITALICS.code)
        out = out.replace(IrcFormattingCodes.UNDERLINE.code, DiscordFormattingCodes.UNDERLINE.code)
        out = out.replace(IrcFormattingCodes.STRIKETHROUGH.code, DiscordFormattingCodes.STRIKETHROUGH.code)
        out = out.replace(IrcFormattingCodes.MONOSPACE.code, DiscordFormattingCodes.MONOSPACE.code)

        return out
    }

    /**
     * Ensures that IRC formatting chars are balanced, that is even, as there is no requirement
     * for them to be.
     */
    private fun <T : Enum<T>> fixFormattingBalance(message: String, values: Array<T>): String {
        var out = message

        for (formattingCode in values) {
            if (countSubstring(out, formattingCode.toString()) % 2 != 0) {
                out += formattingCode.toString()
            }
        }

        return out
    }

    /**
     * Takes a message from Discord and translates the formatting to IRC compatible rendering chars
     */
    private fun formatForIrc(message: String): String {
        var out = fixFormattingBalance(message, DiscordFormattingCodes.values()) // required for markdown parsing

        // poor shrug man needs special handling to be spared the markdown parser
        val shrugMan = "¯\\_(ツ)_/¯"
        val shrugKey = UNIQUE_KEY_STR
        out = out.replace(shrugMan, shrugKey)

        out = out.replace(DiscordFormattingCodes.BOLD.code, IrcFormattingCodes.BOLD.code)
        out = out.replace(DiscordFormattingCodes.ITALICS.code, IrcFormattingCodes.ITALICS.code)
        out = out.replace(DiscordFormattingCodes.UNDERLINE.code, IrcFormattingCodes.UNDERLINE.code)
        out = out.replace(DiscordFormattingCodes.STRIKETHROUGH.code, IrcFormattingCodes.STRIKETHROUGH.code)
        out = out.replace(DiscordFormattingCodes.MONOSPACE.code, IrcFormattingCodes.MONOSPACE.code)
        out = out.replace(DiscordFormattingCodes.ITALICS_ALT.code, IrcFormattingCodes.ITALICS.code)

        // put shrug man back
        return out.replace(shrugKey, shrugMan)
    }
}

/**
 * General discord (markdown) formatting codes
 */
enum class DiscordFormattingCodes(val code: String) {
    BOLD("**"),
    ITALICS("*"),
    UNDERLINE("__"),
    STRIKETHROUGH("~~"),
    MONOSPACE_PARA("```"),
    MONOSPACE("`"),
    ITALICS_ALT("_");

    override fun toString(): String = code
}

/**
 * Based on info from https://modern.ircdocs.horse/formatting.html
 */
enum class IrcFormattingCodes(val char: Char) {
    BOLD(0x02.toChar()),
    ITALICS(0x1D.toChar()),
    UNDERLINE(0x1F.toChar()),
    STRIKETHROUGH(0x1E.toChar()),
    MONOSPACE(0x11.toChar()),
    RESET(0x0F.toChar());

    val code: String = char.toString()
    override fun toString(): String = code
}
