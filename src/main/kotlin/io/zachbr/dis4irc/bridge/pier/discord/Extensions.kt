/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.Embed
import io.zachbr.dis4irc.bridge.message.DiscordMessageSnapshot
import io.zachbr.dis4irc.bridge.message.DiscordSender
import io.zachbr.dis4irc.bridge.message.DiscordSource
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.ChannelType
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.entities.sticker.Sticker
import org.slf4j.Logger
import java.net.URLEncoder
import java.time.Instant

const val DISCORD_STICKER_MEDIA_URL = "https://media.discordapp.net/stickers/%%ID%%.%%FILETYPE%%?size=256"
const val LOTTIE_PLAYER_BASE_URL = "https://lottie.zachbr.io"
const val CDN_DISCORDAPP_STICKERS_URL_LENGTH = "https://cdn.discordapp.com/stickers/".length

fun TextChannel.asPlatformSource(): DiscordSource = DiscordSource(this.name, this.idLong)

fun Message.toPlatformMessage(logger: Logger, receiveInstant: Instant = Instant.now(), shouldResolveReference: Boolean = true) : io.zachbr.dis4irc.bridge.message.DiscordMessage {
    // We need to get the guild member in order to grab their display name
    val guildMember = this.guild.getMember(this.author)
    if (guildMember == null && !this.author.isBot) {
        logger.debug("Cannot get Discord guild member from user information: {}!", this.author)
    }

    // handle attachments
    val attachmentUrls = parseAttachments(this.attachments)

    // handle custom emojis
    var messageText = this.contentDisplay
    for (customEmoji in this.mentions.customEmojis) {
        attachmentUrls.add(customEmoji.imageUrl)
    }

    // handle stickers
    // todo refactor
    for (sticker in this.stickers) {
        if (messageText.isNotEmpty()) {
            messageText += " "
        }
        messageText += sticker.name

        val url = when (sticker.formatType) {
            Sticker.StickerFormat.LOTTIE -> makeLottieViewerUrl(sticker.iconUrl)
            Sticker.StickerFormat.APNG, Sticker.StickerFormat.PNG -> DISCORD_STICKER_MEDIA_URL.replace("%%ID%%", sticker.id).replace("%%FILETYPE%%", "png")
            Sticker.StickerFormat.UNKNOWN -> null
            else -> {
                logger.debug("Unhandled sticker format type: {}", sticker.formatType)
                null
            }
        }

        if (url != null) {
            attachmentUrls.add(url)
        } else {
            messageText += " <sticker format not supported>"
        }
    }

    // embeds - this only works with channel embeds, not for listening to slash commands and interaction hooks
    val parsedEmbeds = parseEmbeds(embeds)

    // discord replies
    var platformMsgRef: io.zachbr.dis4irc.bridge.message.DiscordMessage? = null
    val discordMsgRef = this.referencedMessage
    if (shouldResolveReference && discordMsgRef != null) {
        platformMsgRef = discordMsgRef.toPlatformMessage(logger, receiveInstant, shouldResolveReference = false) // do not endlessly resolve references
    }

    // forwards
    val snapshots = ArrayList<DiscordMessageSnapshot>()
    for (snapshot in this.messageSnapshots) {
        val snapshotAttachmentUrls = parseAttachments(snapshot.attachments)
        for (customEmoji in this.mentions.customEmojis) {
            snapshotAttachmentUrls.add(customEmoji.imageUrl)
        }

        var snapshotText = snapshot.contentRaw
        val snapshotEmbeds = parseEmbeds(snapshot.embeds)
        // todo refactor
        for (sticker in snapshot.stickers) {
            if (snapshotText.isNotEmpty()) {
                snapshotText += " "
            }
            snapshotText += sticker.name

            val url = when (sticker.formatType) {
                Sticker.StickerFormat.LOTTIE -> makeLottieViewerUrl(sticker.iconUrl)
                Sticker.StickerFormat.APNG, Sticker.StickerFormat.PNG -> DISCORD_STICKER_MEDIA_URL.replace("%%ID%%", sticker.id).replace("%%FILETYPE%%", "png")
                Sticker.StickerFormat.UNKNOWN -> null
                else -> {
                    logger.debug("Unhandled sticker format type: {}", sticker.formatType)
                    null
                }
            }

            if (url != null) {
                snapshotAttachmentUrls.add(url)
            } else {
                snapshotText += " <sticker format not supported>"
            }
        }

        snapshots.add(DiscordMessageSnapshot(
            snapshotText,
            snapshotAttachmentUrls,
            snapshotEmbeds
        ))
    }

    val displayName = guildMember?.effectiveName ?: this.author.name // webhooks won't have an effective name
    val sender = DiscordSender(displayName, this.author.idLong)
    if (this.channelType != ChannelType.TEXT) {
        logger.debug("Encountered unsupported channel type: {}", channelType) // TODO: probably a nicer way to handle this (FIXME: Support other types?)
    }
    val channel = this.channel.asTextChannel().asPlatformSource()
    return io.zachbr.dis4irc.bridge.message.DiscordMessage(
        messageText,
        sender,
        channel,
        receiveInstant,
        attachmentUrls,
        parsedEmbeds,
        snapshots,
        platformMsgRef
    )
}

fun makeLottieViewerUrl(discordCdnUrl: String): String? {
    if (discordCdnUrl.length <= CDN_DISCORDAPP_STICKERS_URL_LENGTH) {
        return null
    }

    val resourcePath = discordCdnUrl.substring(CDN_DISCORDAPP_STICKERS_URL_LENGTH)
    val proxyString = "/stickers/$resourcePath"
    val encodedString = URLEncoder.encode(proxyString, "UTF-8") // has to use look up for Java 8 compat

    return "$LOTTIE_PLAYER_BASE_URL?p=$encodedString"
}

fun parseEmbeds(embeds: List<MessageEmbed>): List<Embed> {
    val parsed = ArrayList<Embed>()
    for (embed in embeds) {
        val strBuilder = StringBuilder()
        val imageUrl = embed.image?.url

        if (embed.title != null) {
            strBuilder.append(embed.title)
        }

        if (embed.title != null && embed.description != null) {
            strBuilder.append(": ")
        }

        if (embed.description != null) {
            strBuilder.append(embed.description)
        }

        if (embed.title != null || embed.description != null) {
            strBuilder.append('\n')
        }

        val fieldsCount = embed.fields.count()
        for ((fi, field) in embed.fields.withIndex()) {
            if (field.name != null) {
                strBuilder.append(field.name)
            }

            if (field.name != null && field.value != null) {
                strBuilder.append(": ")
            }

            if (field.value != null) {
                strBuilder.append(field.value)
            }

            if (fi < fieldsCount - 1) {
                strBuilder.append('\n')
            }
        }

        parsed.add(Embed(strBuilder.toString(), imageUrl))
    }

    return parsed
}

fun parseAttachments(attachments: List<Message.Attachment>) : MutableList<String> {
    val attachmentUrls = ArrayList<String>()
    for (attachment in attachments) {
        var url = attachment.url
        if (attachment.isImage) {
            url = attachment.proxyUrl
        }

        attachmentUrls.add(url)
    }
    return attachmentUrls
}