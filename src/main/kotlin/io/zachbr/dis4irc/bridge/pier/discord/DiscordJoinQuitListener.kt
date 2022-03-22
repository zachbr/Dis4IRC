/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2022 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.sourceFromUnknown
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class DiscordJoinQuitListener(private val pier: DiscordPier) : ListenerAdapter() {
    private val logger = pier.logger

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val channel = event.guild.systemChannel
        val source = channel?.asBridgeSource() ?: sourceFromUnknown(PlatformType.DISCORD)

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD JOIN ${event.user.name}")

        val sender = BOT_SENDER
        val message = Message("${event.user.name} has joined the Discord", sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val channel = event.guild.systemChannel
        val source = channel?.asBridgeSource() ?: sourceFromUnknown(PlatformType.DISCORD)

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD PART ${event.user.name}")

        val sender = BOT_SENDER
        val message = Message("${event.user.name} has left the Discord", sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
