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

import io.zachbr.dis4irc.bridge.command.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Sender
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.element.User
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent

/**
 * Responsible for listening to incoming IRC messages and filtering garbage
 */
class IrcListener(private val pier: IrcPier) {
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
        val channel = Channel(event.channel.name, null, Channel.Type.IRC)
        val msgContent = "${event.user.nick} (${event.user.userString}@${event.user.host}) has joined ${event.channel.name}"
        val message = Message(msgContent, sender, channel, receiveTimestamp, null)
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
        val channel = Channel(event.channel.name, null, Channel.Type.IRC)
        val msgContent = "${event.user.nick} (${event.user.userString}@${event.user.host}) has left ${event.channel.name}"
        val message = Message(msgContent, sender, channel, receiveTimestamp, null)
        pier.sendToBridge(message)
    }

    @Handler
    fun onMessage(event: ChannelMessageEvent) {
        // ignore messages sent by this bot
        if (event.actor.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC MSG ${event.channel.name} ${event.actor.nick}: ${event.message}")

        val nickserv = getNickServAccountName(event.actor)
        val sender = Sender(event.actor.nick, null, nickserv)
        val channel = Channel(event.channel.name, null, Channel.Type.IRC)
        val message = Message(event.message, sender, channel, receiveTimestamp, null)
        pier.sendToBridge(message)
    }

    /**
     * Gets a user's nickserv account name or null if it cannot be found
     */
    private fun getNickServAccountName(user: User): String? {
        return if (user.account.isPresent) user.account.get() else null
    }
}
