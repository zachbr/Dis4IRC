/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.Dis4IRC
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
import java.lang.Exception

/**
 * Responsible for the connection between Discord and IRC, including message processing hand offs
 */
class Bridge(private val main: Dis4IRC, internal val config: BridgeConfiguration, rawConfig: CommentedConfigurationNode) {
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
        } catch (ex: Exception) { // just catch everything - "conditions that a reasonable application might want to catch"
            logger.error("Unable to initialize bridge connections: $ex")
            ex.printStackTrace()
            this.shutdown(inErr = true)
            return
        }

        logger.info("Bridge initialized and running")
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
    internal fun shutdown(inErr: Boolean = false) {
        logger.debug("Stopping bridge...")

        discordConn.shutdown()
        ircConn.shutdown()

        logger.info("Bridge stopped")
        main.notifyOfBridgeShutdown(this, inErr)
    }

    /**
     * Adds a message's handling time to the bridge's collection for monitoring purposes
     */
    fun updateStatistics(message: Message, timestampOut: Long) {
        statsManager.processMessage(message, timestampOut)
    }
}
