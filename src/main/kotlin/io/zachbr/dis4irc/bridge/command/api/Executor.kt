/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command.api

import io.zachbr.dis4irc.bridge.message.PlatformMessage

interface Executor {
    /**
     * Perform some action when a command is executed
     *
     * @return your desired output or null if you desire no output
     */
    fun onCommand(command: PlatformMessage): String?
}
