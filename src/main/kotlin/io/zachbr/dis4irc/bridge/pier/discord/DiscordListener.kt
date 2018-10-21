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

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.api.Channel
import io.zachbr.dis4irc.api.Message
import io.zachbr.dis4irc.api.Sender
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

/**
 * Responsible for listening to incoming discord messages and filtering garbage
 */
class DiscordListener(private val pier: DiscordPier) : ListenerAdapter() {
    private val logger = pier.logger

    override fun onMessageReceived(event: MessageReceivedEvent?) {
        if (event == null) {
            logger.debug("Null Discord event from JDA")
            return
        }

        // dont bridge itself
        if (event.author.idLong == pier.getBotId()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD " + event.channel?.name + " " + event.author.name + ": " + event.message.contentStripped)

        // We need to get the guild member in order to grab their display name
        val guildMember = event.guild.getMember(event.author)
        if (guildMember == null) {
            logger.debug("Cannot get Discord guild member from user information: ${event.author}!")
        }

        val displayName = guildMember?.effectiveName ?: event.author.name
        val sender = Sender(displayName, event.author.idLong, null)
        val channel = Channel(event.channel.name, event.channel.idLong, Channel.Type.DISCORD)
        val message = Message(event.message.contentDisplay, sender, channel, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
