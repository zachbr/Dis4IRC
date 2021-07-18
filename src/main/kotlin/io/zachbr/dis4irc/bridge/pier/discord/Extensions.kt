/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.message.Source
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageChannel
import net.dv8tion.jda.api.entities.MessageSticker
import org.slf4j.Logger

const val DISCORD_STICKER_MEDIA_URL = "https://media.discordapp.net/stickers/%%ID%%.%%FILETYPE%%?size=256"

fun MessageChannel.asBridgeSource(): Source = Source(this.name, this.idLong, PlatformType.DISCORD)

fun Message.toBridgeMsg(logger: Logger, receiveTimestamp: Long = System.nanoTime(), shouldResolveReference: Boolean = true) : io.zachbr.dis4irc.bridge.message.Message {
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

    // handle stickers
    for (sticker in this.stickers) {
        if (messageText.isNotEmpty()) {
            messageText += " "
        }
        messageText += sticker.name

        // JDA always uses a cdn.discordapp.com URL for stickers, even though it only appears to be relevant for JSON
        // lottie stickers, and not the image based ones. The image based ones appear to just use the normal
        // media.discordapp.com URL. So we can just build that media URL ourselves based on the sticker type and ID.
        val urlExt = when (sticker.formatType) {
            MessageSticker.StickerFormat.LOTTIE -> null // this is technically "json" but we can't deal with this format
            MessageSticker.StickerFormat.APNG -> "png"
            MessageSticker.StickerFormat.PNG -> "png"
            MessageSticker.StickerFormat.UNKNOWN -> null
            else -> {
                logger.debug("Unhandled sticker format type: ${sticker.formatType}")
                null
            }
        }

        if (urlExt != null) {
            attachmentUrls.add(DISCORD_STICKER_MEDIA_URL.replace("%%ID%%", sticker.id).replace("%%FILETYPE%%", urlExt))
        } else {
            messageText += " <sticker format not supported>"
        }
    }

    // discord replies
    var bridgeMsgRef: io.zachbr.dis4irc.bridge.message.Message? = null
    val discordMsgRef = this.referencedMessage
    if (shouldResolveReference && discordMsgRef != null) {
        bridgeMsgRef = discordMsgRef.toBridgeMsg(logger, receiveTimestamp, shouldResolveReference = false) // do not endlessly resolve references
    }

    val displayName = guildMember?.effectiveName ?: this.author.name // webhooks won't have an effective name
    val sender = Sender(displayName, this.author.idLong, null)
    return io.zachbr.dis4irc.bridge.message.Message(
        messageText,
        sender,
        this.channel.asBridgeSource(),
        receiveTimestamp,
        attachmentUrls,
        bridgeMsgRef
    )
}
