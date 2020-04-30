/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2020 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.message.Source
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import org.slf4j.Logger

fun MessageChannel.asBridgeSource(): Source = Source(this.name, this.idLong, PlatformType.DISCORD)
fun Message.toBridgeMsg(logger: Logger, receiveTimestamp: Long = System.nanoTime()): io.zachbr.dis4irc.bridge.message.Message {
    // We need to get the guild member in order to grab their display name
    val guildMember = this.guild.getMember(this.author)
    if (guildMember == null && !this.author.isBot) {
        logger.debug("Cannot get Discord guild member from user information: ${this.author}!")
    }

    // handle attachments
    val attachmentUrls = ArrayList<String>()
    for (attachment in this.attachments) {
        var url = attachment.url
        if (attachment.isImage) {
            url = attachment.proxyUrl
        }

        attachmentUrls.add(url)
    }

    // handle custom emotes
    var messageText = this.contentDisplay

    for (emote in this.emotes) {
        messageText = messageText.replace(":${emote.name}:", "")
        attachmentUrls.add(emote.imageUrl)
    }

    val displayName = guildMember?.effectiveName ?: this.author.name // webhooks won't have an effective name
    val sender = Sender(displayName, this.author.idLong, null)
    return io.zachbr.dis4irc.bridge.message.Message(messageText, sender, this.channel.asBridgeSource(), receiveTimestamp, attachmentUrls)
}
