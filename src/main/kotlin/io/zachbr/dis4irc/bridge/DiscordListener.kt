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

package io.zachbr.dis4irc.bridge

import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

class DiscordListener(private val bridge: Bridge) : ListenerAdapter() {
    private val logger = bridge.logger

    override fun onMessageReceived(event: MessageReceivedEvent?) {
        if (event == null) {
            logger.debug("Null Discord event from JDA")
            return
        }

        // dont bridge itself
        if (event.author.idLong == bridge.getDiscordBotId()) {
            return
        }

        // dont bridge unrelated channels
        if (!bridge.hasMappingFor(event.channel)) {
            return
        }

        logger.debug("DISCORD " + event.channel?.name + " " + event.author.name + ": " + event.message.contentStripped)
        bridge.toIRC(event.author.name, event.channel, event.message.contentStripped)
    }
}
