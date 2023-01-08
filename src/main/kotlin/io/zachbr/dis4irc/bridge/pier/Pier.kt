/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2023 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier

import io.zachbr.dis4irc.bridge.message.Message

interface Pier {

    /**
     * Starts a pier, connecting it to whatever backend it needs, and readying it for use
     */
    fun start()

    /**
     * Called when the bridge is to be safely shutdown
     */
    fun onShutdown()

    /**
     * Sends a message through this pier
     */
    fun sendMessage(targetChan: String, msg: Message)
}
