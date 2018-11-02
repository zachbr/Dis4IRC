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

import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent

class IrcJoinQuitListener(private val pier: IrcPier) {
    private val logger = pier.logger

    @Handler
    fun onUserJoinChan(event: ChannelJoinEvent) {
        // don't log our own joins
        if (event.user.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC JOIN ${event.channel.name}  ${event.user.nick}")

        val sender = BOT_SENDER
        val source = event.channel.asBridgeSource()
        val msgContent = "${event.user.nick} (${event.user.userString}@${event.user.host}) has joined ${event.channel.name}"
        val message = Message(msgContent, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }

    @Handler
    fun onUserLeaveChan(event: ChannelPartEvent) {
        // don't log our own leaving
        if (event.user.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC PART ${event.channel.name}  ${event.user.nick}")

        val sender = BOT_SENDER
        val source = event.channel.asBridgeSource()
        val msgContent = "${event.user.nick} (${event.user.userString}@${event.user.host}) has left ${event.channel.name}"
        val message = Message(msgContent, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
