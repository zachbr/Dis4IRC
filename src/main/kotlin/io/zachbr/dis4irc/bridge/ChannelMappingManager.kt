/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.IrcSource
import io.zachbr.dis4irc.bridge.message.PlatformSource
import java.util.Locale

/**
 * Responsible for maintaining the channel-to-channel mappings between IRC and Discord
 */
class ChannelMappingManager(conf: BridgeConfiguration) {
    private val discord2Irc = HashMap<String, String>()
    private val irc2Discord: Map<String, String>

    init {
        for (mapping in conf.channelMappings) {
            discord2Irc[mapping.discordChannel] = mapping.ircChannel.lowercase(Locale.ENGLISH)
        }

        // reverse
        irc2Discord = discord2Irc.entries.associateBy({ it.value }) { it.key }
    }

    /**
     * Gets the opposite channel mapping for the given channel
     */
    internal fun getMappingFor(source: PlatformSource): String? {
        return when (source) {
            is IrcSource -> ircMappingByName(source.channelName)
            is DiscordSource -> discordMappingByName(source.channelId.toString()) ?: discordMappingByName(source.channelName)
        }
    }

    /**
     * Gets the IRC channel to bridge to based on the given string
     */
    private fun discordMappingByName(discordId: String): String? {
        return discord2Irc[discordId]
    }

    /**
     * Gets the discord channel identifier to bridge to based on the IRC channel name
     */
    private fun ircMappingByName(ircChannel: String): String? {
        return irc2Discord[ircChannel.lowercase(Locale.ENGLISH)]
    }
}
