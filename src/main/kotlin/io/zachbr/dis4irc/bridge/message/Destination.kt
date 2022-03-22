/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2022 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

enum class Destination {
    /**
     * Only send back to the source
     */
    ORIGIN,
    /**
     * Only send to the opposite side of the bridge
     */
    OPPOSITE,
    /**
     * Send to both sides of the bridge
     */
    BOTH,
    /**
     * Only send to IRC
     */
    IRC,
    /**
     * Only send to Discord
     */
    DISCORD
}
