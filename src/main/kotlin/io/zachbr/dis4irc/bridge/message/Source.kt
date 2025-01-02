/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

data class Source(
    /**
     * Name of the channel
     */
    val channelName: String,
    /**
     * Discord ID of the channel
     */
    val discordId: Long?,
    /**
     * Source type
     */
    val type: PlatformType
)

fun sourceFromUnknown(platform: PlatformType): Source { // TODO - better solutions elsewhere?
    return Source("Unknown", 0, platform)
}
