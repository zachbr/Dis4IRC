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
import net.dv8tion.jda.api.components.Component
import net.dv8tion.jda.api.components.container.Container
import net.dv8tion.jda.api.components.filedisplay.FileDisplay
import net.dv8tion.jda.api.components.label.Label
import net.dv8tion.jda.api.components.section.Section
import net.dv8tion.jda.api.components.separator.Separator
import net.dv8tion.jda.api.components.textdisplay.TextDisplay
import net.dv8tion.jda.api.components.thumbnail.Thumbnail
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.MessageType
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
    var sender = DiscordSender(displayName, this.author.idLong)

    // slash command handling
    val interactionMeta = this.interactionMetadata
    if (this.type == MessageType.SLASH_COMMAND && embeds.isEmpty() && interactionMeta != null) {
        if (messageText.isBlank() && components.isEmpty()) { // command invocation
            sender = DiscordSender(interactionMeta.user.effectiveName, interactionMeta.user.idLong)
            messageText = "*used the /" + this.interaction?.name + " command*"
        }

        // treat components as embeds, a richer representation is probably needed but one problem at a time
        if (this.components.isNotEmpty()) {
            parseComponentsAsEmbeds(this.components, parsedEmbeds)
        }
    }

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
        timeCreated,
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

fun parseEmbeds(embeds: List<MessageEmbed>): MutableList<Embed> {
    val parsed = ArrayList<Embed>()
    for (embed in embeds) {
        val strBuilder = StringBuilder()
        val imageUrl = embed.image?.url

        if (embed.title != null) {
            strBuilder.append("**" + embed.title + "**")
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

fun parseComponentsAsEmbeds(components: List<Component>, embeds: MutableList<Embed>) {
    embeds.addAll(parseComponentsAsEmbeds(components))
}

private data class ComponentEmbedBuilder(
    val text: StringBuilder = StringBuilder(),
    var imageUrl: String? = null
) {
    fun append(value: String?) {
        if (value.isNullOrBlank()) {
            return
        }

        if (text.isNotEmpty() && !text.endsWith("\n")) {
            text.append('\n')
        }

        text.append(value)
    }

    fun appendBlankLine() {
        if (text.isNotEmpty() && !text.endsWith("\n\n")) {
            if (!text.endsWith("\n")) {
                text.append('\n')
            }

            text.append('\n')
        }
    }

    fun append(embed: Embed) {
        append(embed.string)
        captureImage(embed.imageUrl)
    }

    fun captureImage(url: String?) {
        if (imageUrl == null && !url.isNullOrBlank()) {
            imageUrl = url
        }
    }

    fun toEmbed(): Embed? {
        val string = text.toString().trim()
        if (string.isBlank() && imageUrl == null) {
            return null
        }

        return Embed(string, imageUrl)
    }
}

private fun parseComponentsAsEmbeds(components: List<Component>): List<Embed> {
    val embeds = ArrayList<Embed>()

    for (component in components) {
        when (component) {
            is Separator -> continue
            is Container -> embeds.addAll(parseContainerAsEmbeds(component))
            is Section -> parseSectionAsEmbed(component)?.let { embeds.add(it) }
            else -> {
                val builder = ComponentEmbedBuilder()
                if (appendLeafComponent(builder, component)) {
                    builder.toEmbed()?.let { embeds.add(it) }
                }
            }
        }
    }

    return embeds
}

private fun parseContainerAsEmbeds(container: Container): List<Embed> {
    val embeds = ArrayList<Embed>()

    for (component in container.components) {
        when (component) {
            is Separator -> continue
            is Section -> parseSectionAsEmbed(component)?.let { embeds.add(it) }
            is Container -> embeds.addAll(parseContainerAsEmbeds(component))
            else -> appendLooseComponent(embeds, component)
        }
    }

    return embeds
}

private fun parseSectionAsEmbed(section: Section): Embed? {
    val builder = ComponentEmbedBuilder()

    for (component in section.contentComponents) {
        when (component) {
            is Separator -> builder.appendBlankLine()
            is Container -> parseContainerAsEmbeds(component).forEach { builder.append(it) }
            is Section -> parseSectionAsEmbed(component)?.let { builder.append(it) }
            else -> appendLeafComponent(builder, component, boldLabels = true)
        }
    }

    val accessory = section.accessory
    if (accessory is Thumbnail) {
        appendLeafComponent(builder, accessory)
    }

    return builder.toEmbed()
}

private fun appendLooseComponent(embeds: MutableList<Embed>, component: Component) {
    val builder = ComponentEmbedBuilder()
    if (!appendLeafComponent(builder, component)) {
        return
    }

    val embed = builder.toEmbed() ?: return
    if (embeds.isEmpty()) {
        embeds.add(embed)
        return
    }

    val previous = embeds.last()
    previous.string = appendText(previous.string, embed.string)

    if (previous.imageUrl == null && embed.imageUrl != null) {
        embeds[embeds.lastIndex] = Embed(previous.string, embed.imageUrl)
    }
}

private fun appendLeafComponent(builder: ComponentEmbedBuilder, component: Component, boldLabels: Boolean = false): Boolean {
    when (component) {
        is Label -> {
            val text = cleanComponentText(component.label)
            builder.append(if (boldLabels && text != null) "**$text**" else text)
        }

        is TextDisplay -> {
            builder.append(cleanComponentText(component.content))
        }

        is FileDisplay -> {
            builder.append(component.url)
        }

        is Thumbnail -> {
            builder.append(cleanComponentText(component.description))
            builder.captureImage(component.url)
        }

        else -> return false
    }

    return true
}

private fun appendText(existing: String?, value: String?): String? {
    if (value.isNullOrBlank()) {
        return existing
    }

    if (existing.isNullOrBlank()) {
        return value
    }

    return "$existing\n$value"
}

private fun cleanComponentText(value: String?): String? {
    if (value == null) {
        return null
    }

    return value
        // todo - do not love the amount of reformatting being done here as regex
        // markdown headings from components, move to normal bold e.g. "### Title" -> "**Title**"
        .replace(Regex("""(?m)^#{1,6}\s+(.+)$""")) {
            "**${it.groupValues[1]}**"
        }

        // markdown links e.g. "[@display](https://url)" -> "@display (https://url)"
        .replace(Regex("""\[([^\]]+)]\(([^)]+)\)""")) {
            "${it.groupValues[1]} (${it.groupValues[2]})"
        }

        // custom emoji, e.g. "<:reply:123>" or "<a:party:123>" -> ":reply:" / ":party:"
        .replace(Regex("""<a?:([A-Za-z0-9_]+):\d+>""")) {
            ":${it.groupValues[1]}:"
        }

        // remove discord timestamps
        .replace(Regex("""<t:\d+:[tTdDfFR]>"""), "")

        // Collapse extra horizontal whitespace left by removed markup.
        .replace(Regex("""[ \t]+"""), " ")

        // Avoid excessive blank lines.
        .replace(Regex("""\n{3,}"""), "\n\n")

        .trim()
        .ifBlank { null }
}

fun parseAttachments(attachments: List<Message.Attachment>) : MutableList<String> {
    return attachments
        .mapTo(ArrayList()) { attachment ->
            if (attachment.isImage) attachment.proxyUrl else attachment.url
        }
}
