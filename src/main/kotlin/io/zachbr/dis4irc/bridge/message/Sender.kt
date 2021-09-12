/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

internal val BOT_SENDER = Sender("Bridge", "Bridge", null, null)

data class Sender(
    /**
     * User's display name, this is *not* guaranteed to be unique or secure
     */
    val displayName: String,
    /**
     * User's tag name, this is guaranteed to be unique
     */
    val tagName: String,
    /**
     * User's discord snowflake id, or null if the message originated from Discord
     */
    val discordId: Long?,
    /**
     * User's nickserv account name, or null if the message originated from IRC or the IRC network doesn't support it
     */
    val ircNickServ: String?
)
