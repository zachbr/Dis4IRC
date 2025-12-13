/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command.executors

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.command.api.Executor
import io.zachbr.dis4irc.bridge.message.IrcSource
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.mutator.mutators.TranslateFormatting
import io.zachbr.dis4irc.bridge.pier.irc.IrcMessageFormatter
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.min

private const val PAGE_SIZE = 5
private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

class PinnedMessagesCommand(private val bridge: Bridge) : Executor {
    override fun onCommand(command: PlatformMessage): String? {
        if (command.source !is IrcSource) {
            bridge.logger.debug("Ignoring request for pinned messages because it originates from Discord")
            return null
        }

        val args = command.contents.split(' ')
        var requestedPage = 1
        if (args.size > 1) {
            requestedPage = try {
                args[1].toInt()
            } catch (_: NumberFormatException) {
                bridge.ircConn.sendNotice(command.sender.displayName, "Invalid page number provided.")
                return null
            }
        }

        if (requestedPage < 1) {
            bridge.ircConn.sendNotice(command.sender.displayName, "Page number must be 1 or greater.")
            return null
        }

        val mappedChannel = bridge.channelMappings.getMappingFor(command.source) ?: throw IllegalStateException("No mapping for source channel: ${command.source}?!?")
        bridge.discordConn.getPinnedMessages(mappedChannel) { pinned ->
            if (pinned.isNullOrEmpty()) {
                bridge.ircConn.sendNotice(command.sender.displayName, "There are no pinned messages for ${command.source.channelName}.")
                return@getPinnedMessages
            }

            val totalMessages = pinned.size
            val totalPages = (totalMessages + PAGE_SIZE - 1) / PAGE_SIZE
            if (requestedPage > totalPages) {
                bridge.ircConn.sendNotice(command.sender.displayName, "Page $requestedPage does not exist. Total pages: $totalPages")
                return@getPinnedMessages
            }

            val startIndex = (requestedPage - 1) * PAGE_SIZE
            val endIndex = min(startIndex + PAGE_SIZE, totalMessages)
            val pageMessages = pinned.subList(startIndex, endIndex)

            bridge.ircConn.sendNotice(command.sender.displayName, "--- Pinned Messages (Page $requestedPage/$totalPages) ---")
            for (msg in pageMessages) {
                bridge.mutatorManager.applyMutator(TranslateFormatting::class.java, msg)
                val msgContent = StringBuilder()
                    .append(msg.sentTimestamp.format(dateFormatter))
                    .append(" at ")
                    .append(msg.sentTimestamp.format(timeFormatter))
                    .append(' ')
                    .append(IrcMessageFormatter.format(msg, bridge.config).joinToString(" "))

                bridge.ircConn.sendNotice(command.sender.displayName, msgContent.toString())
            }
        }

        return null // don't send a message publicly
    }
}
