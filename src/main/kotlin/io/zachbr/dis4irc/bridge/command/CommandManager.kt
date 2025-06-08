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
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.BridgeSender
import io.zachbr.dis4irc.bridge.message.CommandMessage
import io.zachbr.dis4irc.bridge.message.Destination
import org.spongepowered.configurate.CommentedConfigurationNode
import java.time.Instant

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
    fun processCommandMessage(command: BridgeMessage) {
        val platMessage = command.message
        val split = platMessage.contents.split(" ")
        val trigger = split[0].substring(COMMAND_PREFIX.length, split[0].length) // strip off command prefix
        val executor = getExecutorFor(trigger) ?: return

        logger.debug("Passing command to executor: {}", executor)
        val result = executor.onCommand(platMessage)
        if (result != null) {
            // submit as new message
            val resultMessage = BridgeMessage(CommandMessage(result, BridgeSender, platMessage.source, Instant.now()))
            resultMessage.destination = Destination.BOTH // theoretically the source message was bridged, so the result should go to both places
            bridge.submitMessage(resultMessage)
        }
    }
}
