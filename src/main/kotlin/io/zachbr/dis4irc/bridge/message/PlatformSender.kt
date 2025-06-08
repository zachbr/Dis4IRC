/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

sealed interface PlatformSender {
    val displayName: String
}

data class IrcSender(
    override val displayName: String,
    val nickServAccount: String?
) : PlatformSender

data class DiscordSender(
    override val displayName: String,
    val userId: Long
) : PlatformSender

object BridgeSender : PlatformSender {
    override val displayName: String = "Bridge"
}
