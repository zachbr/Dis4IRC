/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

sealed interface PlatformSource {
    val channelName: String
}

data class IrcSource(
    override val channelName: String
) : PlatformSource

data class DiscordSource(
    override val channelName: String,
    val channelId: Long
) : PlatformSource
