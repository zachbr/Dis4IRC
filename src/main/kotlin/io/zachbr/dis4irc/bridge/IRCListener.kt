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

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

/**
 * Responsible for listening to incoming IRC messages and filtering garbage
 */
class IRCListener(private val bridge: Bridge) {
    private val logger = bridge.logger

    @Handler
    fun onUserJoinChan(event: ChannelJoinEvent) {
        logger.debug("IRC JOIN " + event.channel.name + " " + event.actor.nick)
    }

    @Handler
    fun onMessage(event: ChannelMessageEvent) {
        // dont bridge itself
        if (event.actor.nick == bridge.getIrcBotNick()) {
            return
        }

        logger.debug("IRC " + event.channel.name + " " + event.actor.nick + ": " + event.message)
        bridge.handleMessageFromIrc(event.actor, event.channel, event.message)
    }
}
