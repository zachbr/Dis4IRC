/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier

import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.message.Message

interface Pier {

    /**
     * Initializes a pier, connecting it to whatever backend it needs, and readying it for use
     */
    fun init(config: BridgeConfiguration)

    /**
     * Safely shuts down a pier
     */
    fun shutdown()

    /**
     * Sends a message through this pier
     */
    fun sendMessage(targetChan: String, msg: Message)
}
