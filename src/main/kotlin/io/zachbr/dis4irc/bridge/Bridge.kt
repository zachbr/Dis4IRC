/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.bridge.command.COMMAND_PREFIX
import io.zachbr.dis4irc.bridge.command.CommandManager
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.mutator.MutatorManager
import io.zachbr.dis4irc.bridge.pier.Pier
import io.zachbr.dis4irc.bridge.pier.discord.DiscordPier
import io.zachbr.dis4irc.bridge.pier.irc.IrcPier
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Responsible for the connection between Discord and IRC, including message processing hand offs
 */
class Bridge(private val config: BridgeConfiguration, rawConfig: CommentedConfigurationNode) {
    internal val logger = LoggerFactory.getLogger(config.bridgeName) ?: throw IllegalStateException("Could not init logger")

    private val channelMappings = ChannelMappingManager(config)
    private val commandManager = CommandManager(this, rawConfig.getNode("commands"))
    private val mutatorManager = MutatorManager(this, rawConfig.getNode("mutators"))
    internal val statsManager = StatisticsManager(this)

    private val discordConn: Pier
    private val ircConn: Pier

    init {
        discordConn = DiscordPier(this)
        ircConn = IrcPier(this)
    }

    /**
     * Connects to IRC and Discord
     */
    fun startBridge() {
        logger.debug(config.toString())

        try {
            discordConn.init(config)
            ircConn.init(config)
        } catch (ex: IOException) {
            logger.error("IOException while initializing connections: $ex")
            ex.printStackTrace()
            this.shutdown()
        } catch (ex: IllegalArgumentException) {
            logger.error("IllegalArgumentException while initializing connections: $ex")
            ex.printStackTrace()
            this.shutdown()
        }
    }

    /**
     * Bridges communication between the two piers
     */
    internal fun submitMessage(messageIn: Message) {
        val bridgeTarget: String? = channelMappings.getMappingFor(messageIn.source)

        if (bridgeTarget == null) {
            logger.debug("Discarding message with no bridge target from: ${messageIn.source}")
            return
        }

        // mutate message contents
        val mutatedMessage = mutatorManager.applyMutators(messageIn) ?: return

        if (mutatedMessage.shouldSendTo(PlatformType.IRC)) {
            val target: String = if (mutatedMessage.source.type == PlatformType.IRC) mutatedMessage.source.channelName else bridgeTarget
            ircConn.sendMessage(target, mutatedMessage)
        }

        if (mutatedMessage.shouldSendTo(PlatformType.DISCORD)) {
            val target = if (mutatedMessage.source.type == PlatformType.DISCORD) mutatedMessage.source.discordId.toString() else bridgeTarget
            discordConn.sendMessage(target, mutatedMessage)
        }

        // command handling
        if (mutatedMessage.contents.startsWith(COMMAND_PREFIX) && !mutatedMessage.originatesFromBridgeItself()) {
            commandManager.processCommandMessage(mutatedMessage)
        }
    }

    /**
     * Clean up and disconnect from the IRC and Discord platforms
     */
    internal fun shutdown() {
        logger.info("Stopping...")

        discordConn.shutdown()
        ircConn.shutdown()

        logger.info("${config.bridgeName} stopped")
    }

    /**
     * Adds a message's handling time to the bridge's collection for monitoring purposes
     */
    fun updateStatistics(message: Message, timestampOut: Long) {
        statsManager.processMessage(message, timestampOut)
    }
}
