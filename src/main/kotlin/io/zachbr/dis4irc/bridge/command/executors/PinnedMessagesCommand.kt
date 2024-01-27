/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2024 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command.executors

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.command.api.Executor
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.mutator.mutators.TranslateFormatting

class PinnedMessagesCommand(private val bridge: Bridge) : Executor {
    override fun onCommand(command: Message): String? {
        if (command.source.type != PlatformType.IRC) {
            bridge.logger.debug("Ignoring request for pinned messages because it originates from Discord")
            return null
        }

        val mappedChannel = bridge.channelMappings.getMappingFor(command.source) ?: throw IllegalStateException("No mapping for source channel: ${command.source}?!?")
        val pinnedMessages = bridge.discordConn.getPinnedMessages(mappedChannel) ?: return null

        for (msg in pinnedMessages) {
            bridge.mutatorManager.applyMutator(TranslateFormatting::class.java, msg)
            val senderInfo = bridge.ircConn.createMessagePrefix(msg)
            var msgContent = msg.contents

            if (msg.attachments != null && msg.attachments.isNotEmpty()) {
                msg.attachments.forEach { msgContent += " $it"}
            }

            bridge.ircConn.sendNotice(command.sender.displayName, "$senderInfo $msgContent")
        }

        return null // don't send a message publicly
    }

}
