/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

import java.time.Instant

sealed interface PlatformMessage {
    var contents: String
    val sender: PlatformSender
    val source: PlatformSource
    val timestamp: Instant
}

data class IrcMessage(
    override var contents: String,
    override val sender: IrcSender,
    override val source: IrcSource,
    override val timestamp: Instant
) : PlatformMessage

data class DiscordMessage(
    override var contents: String,
    override val sender: DiscordSender,
    override val source: DiscordSource,
    override val timestamp: Instant,
    override val attachments: List<String> = emptyList(),
    override val embeds: List<Embed> = emptyList(),
    val snapshots: List<DiscordContentBase> = emptyList(),
    val referencedMessage: DiscordMessage? = null
) : PlatformMessage, DiscordContentBase

data class CommandMessage(
    override var contents: String,
    override val sender: BridgeSender,
    override val source: PlatformSource,
    override val timestamp: Instant
) : PlatformMessage
