/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

// todo refactor - just get this working, whole structure needs refactoring
data class MessageSnapshot (
    /**
     * Raw content with markdown, mentions, etc. of the forward
     */
    var content: String,
    /**
     * A list of attachment URLs on the message
     */
    val attachments: MutableList<String> = ArrayList(),
    /**
     * Embeds associated with the message.
     */
    val embeds: List<Embed> = ArrayList(),
)
