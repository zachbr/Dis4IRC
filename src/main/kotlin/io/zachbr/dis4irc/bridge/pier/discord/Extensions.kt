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
import java.net.URLEncoder

const val DISCORD_STICKER_MEDIA_URL = "https://media.discordapp.net/stickers/%%ID%%.%%FILETYPE%%?size=256"
const val LOTTIE_PLAYER_BASE_URL = "https://lottie.zachbr.io"
const val CDN_DISCORDAPP_STICKERS_URL_LENGTH = "https://cdn.discordapp.com/stickers/".length

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
        attachmentUrls.add(emote.imageUrl)
    }

    // handle stickers
    for (sticker in this.stickers) {
        if (messageText.isNotEmpty()) {
            messageText += " "
        }
        messageText += sticker.name

        val url = when (sticker.formatType) {
            MessageSticker.StickerFormat.LOTTIE -> makeLottieViewerUrl(sticker.iconUrl)
            MessageSticker.StickerFormat.APNG, MessageSticker.StickerFormat.PNG -> DISCORD_STICKER_MEDIA_URL.replace("%%ID%%", sticker.id).replace("%%FILETYPE%%", "png")
            MessageSticker.StickerFormat.UNKNOWN -> null
            else -> {
                logger.debug("Unhandled sticker format type: ${sticker.formatType}")
                null
            }
        }

        if (url != null) {
            attachmentUrls.add(url)
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
    val tagName = guildMember?.user?.asTag ?: this.author.name // webhooks won't have a tag
    val sender = Sender(displayName, tagName, this.author.idLong, null)
    return io.zachbr.dis4irc.bridge.message.Message(
        messageText,
        sender,
        this.channel.asBridgeSource(),
        receiveTimestamp,
        attachmentUrls,
        bridgeMsgRef
    )
}

fun makeLottieViewerUrl(discordCdnUrl: String): String? {
    if (discordCdnUrl.length <= CDN_DISCORDAPP_STICKERS_URL_LENGTH) {
        return null
    }

    val resourcePath = discordCdnUrl.substring(CDN_DISCORDAPP_STICKERS_URL_LENGTH)
    val proxyString = "/stickers/$resourcePath"
    val encodedString = URLEncoder.encode(proxyString, "UTF-8") // has to use look up for Java 8 compat

    return "$LOTTIE_PLAYER_BASE_URL?p=$encodedString";
}
