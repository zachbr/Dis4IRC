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

import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.message.Source
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

/**
 * Responsible for listening to incoming discord messages and filtering garbage
 */
class DiscordListener(private val pier: DiscordPier) : ListenerAdapter() {
    private val logger = pier.logger

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent?) {
        if (event == null) {
            logger.debug("Null Discord join event from JDA")
            return
        }

        val channel = event.guild.systemChannel
        val source = Source(channel.name, channel.idLong, Source.Type.DISCORD)

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD JOIN ${event.user.name}")

        val sender = BOT_SENDER
        val message = Message("${event.user.name} has joined the Discord", sender, source, receiveTimestamp, null)
        pier.sendToBridge(message)
    }

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent?) {
        if (event == null) {
            logger.debug("Null Discord leave event from JDA")
            return
        }

        val channel = event.guild.systemChannel
        val source = Source(channel.name, channel.idLong, Source.Type.DISCORD)

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD PART ${event.user.name}")

        val sender = BOT_SENDER
        val message = Message("${event.user.name} has left the Discord", sender, source, receiveTimestamp, null)
        pier.sendToBridge(message)
    }

    override fun onMessageReceived(event: MessageReceivedEvent?) {
        if (event == null) {
            logger.debug("Null Discord message event from JDA")
            return
        }

        // dont bridge itself
        val source = Source(event.channel.name, event.channel.idLong, Source.Type.DISCORD)
        if (pier.isThisBot(source, event.author.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD MSG ${event.channel?.name} ${event.author.name} ${event.message.contentStripped}")

        // We need to get the guild member in order to grab their display name
        val guildMember = event.guild.getMember(event.author)
        if (guildMember == null && !event.author.isBot) {
            logger.debug("Cannot get Discord guild member from user information: ${event.author}!")
        }

        // handle attachments
        val attachmentUrls = ArrayList<String>()
        for (attachment in event.message.attachments) {
            var url = attachment.url
            if (attachment.isImage) {
                url = attachment.proxyUrl
            }

            attachmentUrls.add(url)
        }

        // handle custom emotes
        var messageText = event.message.contentDisplay

        for (emote in event.message.emotes) {
            // managed emotes are discord provided (*usually* standard unicode)
            // I've no idea what a fake emote is but it seems like a good thing to avoid
            if (!emote.isManaged || !emote.isFake) {
                messageText = messageText.replaceFirst(":${emote.name}:", "")
                attachmentUrls.add(emote.imageUrl)
            }
        }

        val displayName = guildMember?.effectiveName ?: event.author.name
        val sender = Sender(displayName, event.author.idLong, null)
        val message = Message(messageText, sender, source, receiveTimestamp, attachmentUrls)
        pier.sendToBridge(message)
    }
}
