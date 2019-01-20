/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
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
