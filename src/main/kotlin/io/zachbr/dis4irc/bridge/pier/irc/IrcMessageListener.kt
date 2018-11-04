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

import io.zachbr.dis4irc.bridge.message.Message
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.util.Format

private const val CTCP_ACTION = "ACTION"

/**
 * Responsible for listening to incoming IRC messages and filtering garbage
 */
class IrcMessageListener(private val pier: IrcPier) {
    private val logger = pier.logger

    @Handler
    fun onMessage(event: ChannelMessageEvent) {
        // ignore messages sent by this bot
        if (event.actor.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC MSG ${event.channel.name} ${event.actor.nick}: ${event.message}")

        val sender = event.actor.asBridgeSender()
        val source = event.channel.asBridgeSource()
        val message = Message(event.message, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }

    @Handler
    fun onChannelCtcp(event: ChannelCtcpEvent) {
        // ignore messages sent by this bot
        if (event.actor.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC CTCP ${event.channel.name} ${event.actor.nick}: ${event.message}")

        // if it's not an action we probably don't care
        if (!event.message.startsWith(CTCP_ACTION)) {
            return
        }

        // add italic code and strip off the ctcp type info
        val messageText = "${Format.ITALIC}${event.message.substring(CTCP_ACTION.length + 1)}"

        val sender = event.actor.asBridgeSender()
        val source = event.channel.asBridgeSource()
        val message = Message(messageText, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
