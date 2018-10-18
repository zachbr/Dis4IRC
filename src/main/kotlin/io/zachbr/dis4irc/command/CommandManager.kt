/*
 * This file is part of Dis4IRC.
 *
 * Dis4IRC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dis4IRC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Dis4IRC.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.zachbr.dis4irc.command

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.COMMAND_PREFIX
import io.zachbr.dis4irc.command.api.Executor
import io.zachbr.dis4irc.command.api.SimpleCommand
import io.zachbr.dis4irc.command.executors.SystemInfo

/**
 * Responsible for managing, looking up, and delegating to command executors
 */
class CommandManager(bridge: Bridge) {
    private val executorsByCommand = HashMap<String, Executor>()
    private val logger = bridge.logger

    init {
        registerExecutor("!system", SystemInfo())
    }

    fun registerExecutor(name: String, executor: Executor) {
        if (!name.startsWith(COMMAND_PREFIX)) {
            throw IllegalArgumentException("Executor name registration must start with \"$COMMAND_PREFIX\"")
        }

        executorsByCommand[name] = executor
    }

    fun getExecutorFor(name: String): Executor? {
        return executorsByCommand[name]
    }

    fun processCommandMessage(command: SimpleCommand) {
        val split = command.msg.split(" ")

        val executor = getExecutorFor(split[0]) ?: return

        logger.debug("Passing command to executor: $executor")
        executor.onCommand(command)
    }
}