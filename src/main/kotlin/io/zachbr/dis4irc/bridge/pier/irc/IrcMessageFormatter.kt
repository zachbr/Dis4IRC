/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.IrcConfiguration
import io.zachbr.dis4irc.bridge.message.BridgeSender
import io.zachbr.dis4irc.bridge.message.DiscordContentBase
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.Embed
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.message.PlatformSender
import org.kitteh.irc.client.library.util.Format
import kotlin.math.abs

object IrcMessageFormatter {
    const val ANTI_PING_CHAR = 0x200B.toChar() // zero width space
    private val NICK_COLORS = arrayOf("10", "06", "03", "07", "12", "11", "13", "09", "02")
    private val SAFE_TRIM_CHARS: (Char) -> Boolean = { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
    private val IRC_FORBIDDEN_CHARS = Regex("""[\u0000\r\n]""")

    /**
     * Takes a bridge message and returns a list of lines for sending to IRC.
     */
    fun format(platMessage: PlatformMessage, config: BridgeConfiguration): List<String> {
        // Only really need to format messages from Discord (currently?)
        // Other messages we can just hand back as provided.
        if (platMessage !is DiscordMessage) {
            return splitToIrcLines(platMessage.contents)
        }

        val lines = mutableListOf<String>()

        // discord replies
        if (platMessage.referencedMessage != null && config.irc.discordReplyContextLimit > 0) {
            lines.add(formatReplyHeader(platMessage.referencedMessage, config.irc))
        }

        // primary content
        // forwards will only send as a forward, with zero primary content so we don't need to worry about that.
        if (platMessage.snapshots.isNotEmpty()) {
            lines.addAll(formatForward(platMessage, config.irc))
        } else if (platMessage.contents.isNotBlank() || platMessage.attachments.isNotEmpty() || platMessage.embeds.isNotEmpty()) {
            lines.addAll(formatStandardMessage(platMessage, config.irc))
        }

        return lines
    }

    /**
     * Creates the prefix for each message that tells you who sent it.
     * e.g. "<Z750>: "
     */
    fun createSenderPrefix(sender: PlatformSender, antiPing: Boolean, useColor: Boolean, withAsciiAngleBracket: Boolean = true): String {
        if (sender is BridgeSender) {
            return ""
        }

        var nameOut = sender.displayName
        if (antiPing) {
            nameOut = rebuildWithAntiPing(nameOut)
        }

        if (useColor) {
            val color = getColorCodeForName(sender.displayName)
            nameOut = Format.COLOR_CHAR + color + nameOut + Format.RESET
        }

        return if (withAsciiAngleBracket) {
            "<$nameOut>"
        } else {
            nameOut
        }
    }

    /**
     * Rebuilds a string with the [ANTI_PING_CHAR] character placed strategically.
     */
    fun rebuildWithAntiPing(nick: String): String {
        val builder = StringBuilder()
        val length = nick.length
        for (i in nick.indices) {
            builder.append(nick[i])
            if (i + 1 >= length || !Character.isSurrogatePair(nick[i], nick[i +  1])) {
                if (i % 2 == 0) {
                    builder.append(ANTI_PING_CHAR)
                }
            }
        }
        return builder.toString()
    }

    /**
     * Creates the `Reply to "Sender: context"` line.
     */
    private fun formatReplyHeader(replyTo: DiscordMessage, config: IrcConfiguration): String {
        val limit = config.discordReplyContextLimit
        var context = replyTo.contents.replace("\n", " ")
        if (limit > 0 && context.length > limit) {
            context = context.substring(0, limit - 1) + "..."
        }
        val refSender = createSenderPrefix(replyTo.sender, config.antiPing, config.useNickNameColor, withAsciiAngleBracket = false)
        return "Reply to \"$refSender: $context\""
    }

    /**
     * Formats a forwarded message.
     */
    private fun formatForward(msg: DiscordMessage, config: IrcConfiguration): List<String> {
        val lines = mutableListOf<String>()
        val forwarderName = createSenderPrefix(msg.sender, config.antiPing, config.useNickNameColor, withAsciiAngleBracket = false)
        lines.add("$forwarderName forwarded a message:")

        // forwards currently only contain one message
        val snapshot = msg.snapshots.first()
        val formattedContent = formatContentBlock(snapshot, config)
        lines.addAll(formattedContent.trim(SAFE_TRIM_CHARS).split("\n"))

        return lines
    }

    /**
     * Formats a standard message, handling prefixes.
     */
    private fun formatStandardMessage(msg: DiscordMessage, config: IrcConfiguration): List<String> {
        val lines = mutableListOf<String>()
        val contentLines = formatContentAndAttachments(msg.contents, msg.attachments)
        for (line in contentLines) {
            lines.add(formatDiscordLine(msg.sender, line, config))
        }

        if (config.sendDiscordEmbeds) {
            for (embed in msg.embeds) {
                lines.addAll(formatEmbed(msg.sender, embed, config))
            }
        }

        return lines
    }

    private fun formatContentAndAttachments(contents: String, attachments: List<String>): List<String> {
        var content = contents
        if (attachments.isNotEmpty()) {
            content += " " + attachments.joinToString(" ")
        }

        return splitToIrcLines(content)
    }

    private fun formatEmbed(sender: PlatformSender, embed: Embed, config: IrcConfiguration): List<String> {
        val lines = mutableListOf<String>()
        for (line in splitToIrcLines(embed.string)) {
            lines.add(formatDiscordLine(sender, line, config))
        }

        val imageUrl = embed.imageUrl?.takeIf { it.isNotBlank() }
        if (imageUrl != null) {
            for (line in splitToIrcLines(imageUrl)) {
                lines.add(formatDiscordLine(sender, line, config))
            }
        }

        return lines
    }

    private fun formatDiscordLine(sender: PlatformSender, line: String, config: IrcConfiguration): String {
        val safeLine = sanitizeIrcLine(line)
        val noPrefixPattern = config.noPrefixRegex
        if (noPrefixPattern != null && noPrefixPattern.matcher(safeLine).find()) {
            return safeLine
        }

        val messagePrefix = createSenderPrefix(sender, config.antiPing, config.useNickNameColor)
        return sanitizeIrcLine("$messagePrefix $safeLine")
    }

    private fun splitToIrcLines(value: String?): List<String> {
        if (value.isNullOrBlank()) {
            return emptyList()
        }

        return value
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { sanitizeIrcLine(it) }
            .filter { it.isNotBlank() }
    }

    private fun sanitizeIrcLine(value: String): String {
        return value
            .replace(IRC_FORBIDDEN_CHARS, "")
            .trim(SAFE_TRIM_CHARS)
    }

    /**
     * Helper to handle the same types of formatting across "types" and reduce duplication.
     */
    private fun formatContentBlock(block: DiscordContentBase, config: IrcConfiguration): String {
        var content = block.contents
        val attachments = block.attachments.toMutableList()

        if (config.sendDiscordEmbeds) {
            block.embeds.forEach { embed ->
                embed.string?.takeIf { it.isNotBlank() }?.let {
                    content += "\n$it"
                }
                embed.imageUrl?.takeIf { it.isNotBlank() }?.let {
                    attachments.add(it)
                }
            }
        }

        // Append all attachments
        if (attachments.isNotEmpty()) {
            content += " " + attachments.joinToString(" ")
        }

        return content
    }

    /**
     * Determines the color code to use for the provided nickname.
     * https://github.com/korobi/Web/blob/master/src/Korobi/WebBundle/IRC/Parser/NickColours.php
     */
    private fun getColorCodeForName(nick: String): String {
        var index = 0
        nick.toCharArray().forEach { index += it.code.toByte() }
        return NICK_COLORS[abs(index) % NICK_COLORS.size]
    }
}
