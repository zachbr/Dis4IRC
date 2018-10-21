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

/**
 * Translates Discord's markdown formatting to the IRC formatting codes and vice versa
 */
class TranslateFormatting : Mutator {

    override fun mutate(message: String, source: Channel, sender: Sender): String? {
        return when (source.type) {
            Channel.Type.IRC -> formatForDiscord(message)
            Channel.Type.DISCORD -> formatForIrc(message)
        }
    }

    private fun formatForDiscord(message: String): String {
        TODO("Use forked version of txtmark with GFM and custom emitter support") // https://github.com/rjeschke/txtmark/pulls
    }

    private fun formatForIrc(message: String): String {
        TODO("Use forked version of txtmark with GFM and custom emitter support") // https://github.com/rjeschke/txtmark/pulls
    }

}
