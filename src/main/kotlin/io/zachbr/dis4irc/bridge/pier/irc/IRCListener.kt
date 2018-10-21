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

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.api.Channel
import io.zachbr.dis4irc.api.Message
import io.zachbr.dis4irc.api.Sender
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

/**
 * Responsible for listening to incoming IRC messages and filtering garbage
 */
class IRCListener(private val pier: IRCPier) {
    private val logger = pier.logger

    @Handler
    fun onUserJoinChan(event: ChannelJoinEvent) {
        logger.debug("IRC JOIN " + event.channel.name + " " + event.actor.nick)
    }

    @Handler
    fun onMessage(event: ChannelMessageEvent) {
        // ignore messages sent by this bot
        if (event.actor.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC " + event.channel.name + " " + event.actor.nick + ": " + event.message)

        val nickserv = if (event.actor.account.isPresent) { event.actor.account.get() } else { null }
        val sender = Sender(event.actor.nick, null, nickserv)
        val channel = Channel(event.channel.name, null, Channel.Type.IRC)
        val message = Message(event.message, sender, channel, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
