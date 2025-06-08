/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

sealed interface DiscordContentBase {
    var contents: String
    val attachments: List<String>
    val embeds: List<Embed>
}

data class Embed(
    var string: String?,
    val imageUrl: String?
)

data class DiscordMessageSnapshot(
    override var contents: String,
    override val attachments: List<String> = emptyList(),
    override val embeds: List<Embed> = emptyList()
) : DiscordContentBase
