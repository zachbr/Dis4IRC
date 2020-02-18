/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2020 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command.api

import io.zachbr.dis4irc.bridge.message.Message

interface Executor {
    /**
     * Perform some action when a command is executed
     *
     * @return your desired output or null if you desire no output
     */
    fun onCommand(command: Message): String?
}
