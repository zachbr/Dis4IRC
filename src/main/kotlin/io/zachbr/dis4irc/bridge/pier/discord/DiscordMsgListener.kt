/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2020 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Sender
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Responsible for listening to incoming discord messages and filtering garbage
 */
class DiscordMsgListener(private val pier: DiscordPier) : ListenerAdapter() {
    private val logger = pier.logger

    override fun onGuildMessageReceived(event: GuildMessageReceivedEvent) {
        // dont bridge itself
        val source = event.channel.asBridgeSource()
        if (pier.isThisBot(source, event.author.idLong)) {
            return
        }

        // don't bridge empty messages (discord does this on join)
        if (event.message.contentDisplay.isEmpty() && event.message.attachments.isEmpty()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD MSG ${event.channel.name} ${event.author.name}: ${event.message.contentStripped}")

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
            messageText = messageText.replace(":${emote.name}:", "")
            attachmentUrls.add(emote.imageUrl)
        }

        val displayName = guildMember?.effectiveName ?: event.author.name // webhooks won't have an effective name
        val sender = Sender(displayName, event.author.idLong, null)
        val message = Message(messageText, sender, source, receiveTimestamp, attachmentUrls)
        pier.sendToBridge(message)
    }
}
