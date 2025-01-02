/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.command.api.Executor
import io.zachbr.dis4irc.bridge.command.executors.PinnedMessagesCommand
import io.zachbr.dis4irc.bridge.command.executors.StatsCommand
import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Destination
import io.zachbr.dis4irc.bridge.message.Message
import org.spongepowered.configurate.CommentedConfigurationNode

const val COMMAND_PREFIX: String = "!"

/**
 * Responsible for managing, looking up, and delegating to command executors
 */
class CommandManager(private val bridge: Bridge, config: CommentedConfigurationNode) {
    private val executorsByCommand = HashMap<String, Executor>()
    private val logger = bridge.logger

    init {
        val statsNode = config.node("stats", "enabled")
        if (statsNode.virtual()) {
            statsNode.set("true")
        }
        if (statsNode.boolean) {
            registerExecutor("stats", StatsCommand(bridge))
        }

        val pinnedNode = config.node("pinned", "enabled")
        if (pinnedNode.virtual()) {
            pinnedNode.set("true")
        }
        if (pinnedNode.boolean) {
            registerExecutor("pinned", PinnedMessagesCommand(bridge))
        }
    }

    /**
     * Registers an executor to the given command
     */
    private fun registerExecutor(name: String, executor: Executor) {
        executorsByCommand[name] = executor
    }

    /**
     * Gets the executor for the given command
     */
    private fun getExecutorFor(name: String): Executor? {
        return executorsByCommand[name]
    }

    /**
     * Process a command message, passing it off to the registered executor
     */
    fun processCommandMessage(command: Message) {
        val split = command.contents.split(" ")
        val trigger = split[0].substring(COMMAND_PREFIX.length, split[0].length) // strip off command prefix
        val executor = getExecutorFor(trigger) ?: return

        logger.debug("Passing command to executor: {}", executor)

        command.destination = Destination.BOTH // we want results to go to both sides by default
        val result = executor.onCommand(command)

        if (result != null) {
            command.contents = result
            command.sender = BOT_SENDER

            // submit as new message
            bridge.submitMessage(command)
        }
    }
}
