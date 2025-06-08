/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.DiscordSender
import io.zachbr.dis4irc.bridge.message.DiscordSource
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.time.Instant

class DiscordJoinQuitListener(private val pier: DiscordPier) : ListenerAdapter() {
    private val logger = pier.logger

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent) {
        val channel = event.guild.systemChannel
        val source = channel?.asPlatformSource() ?: DiscordSource("Unknown", 0L) // "Unknown" and 0 for legacy reasons

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveInstant = Instant.now()
        logger.debug("DISCORD JOIN ${event.user.name}")

        val sender = DiscordSender("Discord", 0L)
        val message = DiscordMessage("${event.user.name} has joined the Discord", sender, source, receiveInstant)
        pier.sendToBridge(message)
    }

    override fun onGuildMemberRemove(event: GuildMemberRemoveEvent) {
        val channel = event.guild.systemChannel
        val source = channel?.asPlatformSource() ?: DiscordSource("Unknown", 0L) // "Unknown" and 0 for legacy reasons

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveInstant = Instant.now()
        logger.debug("DISCORD PART ${event.user.name}")

        val sender = DiscordSender("Discord", 0L)
        val message = DiscordMessage("${event.user.name} has left the Discord", sender, source, receiveInstant)
        pier.sendToBridge(message)
    }
}
