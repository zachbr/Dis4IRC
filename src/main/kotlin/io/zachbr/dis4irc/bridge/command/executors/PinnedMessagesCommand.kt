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

class PinnedMessagesCommand(private val bridge: Bridge) : Executor {
    override fun onCommand(command: PlatformMessage): String? {
        if (command.source !is IrcSource) {
            bridge.logger.debug("Ignoring request for pinned messages because it originates from Discord")
            return null
        }

        val mappedChannel = bridge.channelMappings.getMappingFor(command.source) ?: throw IllegalStateException("No mapping for source channel: ${command.source}?!?")
        val pinnedMessages = bridge.discordConn.getPinnedMessages(mappedChannel) ?: return null
        // TODO add max limit (10 most recent?), flood concerns, UX spam, opinionated
        for (msg in pinnedMessages) {
            bridge.mutatorManager.applyMutator(TranslateFormatting::class.java, msg)
            val senderInfo = IrcMessageFormatter.createSenderPrefix(msg.sender, bridge.config.irc.antiPing, bridge.config.irc.useNickNameColor)
            var msgContent = msg.contents
            if (msg.attachments.isNotEmpty()) {
                msg.attachments.forEach { msgContent += " $it"}
            }

            bridge.ircConn.sendNotice(command.sender.displayName, "$senderInfo $msgContent")
        }

        return null // don't send a message publicly
    }

}
