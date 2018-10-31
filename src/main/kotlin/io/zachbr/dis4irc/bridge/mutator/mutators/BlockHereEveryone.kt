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
import io.zachbr.dis4irc.bridge.message.Source
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.mutator.api.Mutator

/**
 * Blocks @here and @everyone from being sent from IRC to Discord
 */
class BlockHereEveryone : Mutator {

    override fun mutate(message: Message): String? {
        // only block from IRC -> Discord, allow them the other way around
        if (message.source.type != Source.Type.IRC) {
            return message.contents
        }

        var out = message.contents

        out = out.replace("@everyone", "at-everyone")
        out = out.replace("@here", "at-here")

        return out
    }
}
