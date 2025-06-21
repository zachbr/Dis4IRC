/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.Dis4IRC
import io.zachbr.dis4irc.bridge.command.COMMAND_PREFIX
import io.zachbr.dis4irc.bridge.command.CommandManager
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.CommandMessage
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.IrcMessage
import io.zachbr.dis4irc.bridge.message.IrcSource
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.mutator.MutatorManager
import io.zachbr.dis4irc.bridge.pier.discord.DiscordPier
import io.zachbr.dis4irc.bridge.pier.irc.IrcPier
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Responsible for the connection between Discord and IRC, including message processing hand-offs
 */
class Bridge(private val main: Dis4IRC, internal val config: BridgeConfiguration) {
    internal val logger = LoggerFactory.getLogger(config.bridgeName) ?: throw IllegalStateException("Could not init logger")

    internal val channelMappings = ChannelMappingManager(config)
    internal val statsManager = StatisticsManager(this)
    private val commandManager = CommandManager(this, config.rawNode.node("commands"))
    internal val mutatorManager = MutatorManager(this, config.rawNode.node("mutators"))

    internal val discordConn = DiscordPier(this)
    internal val ircConn = IrcPier(this)

    /**
     * Connects to IRC and Discord
     */
    fun startBridge() {
        logger.debug(config.toLoggable())

        try {
            discordConn.start()
            ircConn.start()
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
    internal fun submitMessage(bMessage: BridgeMessage) {
        // don't process a message that has no destination
        val bridgeTarget = channelMappings.getMappingFor(bMessage.message.source) ?: run {
            logger.debug("Discarding message with no bridge target from: {}", bMessage.message.source)
            return
        }

        val mutatedMessage = mutatorManager.applyMutators(bMessage) ?: return
        val mutatedPlatformMsg = mutatedMessage.message

        // we only send across the bridge (to the relevant mapping) or back to the same source currently
        val ircSendTarget: String
        val discordSendTarget: String
        when (mutatedPlatformMsg) {
            is IrcMessage -> {
                ircSendTarget = mutatedPlatformMsg.source.channelName
                discordSendTarget = bridgeTarget
            }
            is DiscordMessage -> {
                ircSendTarget = bridgeTarget
                discordSendTarget = mutatedPlatformMsg.source.channelId.toString()
            }
            is CommandMessage -> {
                when (mutatedPlatformMsg.source) {
                    is IrcSource -> {
                        ircSendTarget = mutatedPlatformMsg.source.channelName
                        discordSendTarget = bridgeTarget
                    }
                    is DiscordSource -> {
                        ircSendTarget = bridgeTarget
                        discordSendTarget = mutatedPlatformMsg.source.channelId.toString()
                    }
                }
            }
        }

        if (mutatedMessage.shouldSendTo(PlatformType.IRC)) {
            ircConn.sendMessage(ircSendTarget, mutatedMessage)
        }

        if (mutatedMessage.shouldSendTo(PlatformType.DISCORD)) {
            discordConn.sendMessage(discordSendTarget, mutatedMessage)
        }

        if (mutatedMessage.message.contents.startsWith(COMMAND_PREFIX) && !mutatedMessage.originatesFromBridgeItself()) {
            commandManager.processCommandMessage(mutatedMessage)
        }
    }

    /**
     * Clean up and disconnect from the IRC and Discord platforms
     */
    internal fun shutdown(inErr: Boolean = false) {
        logger.debug("Stopping bridge...")

        discordConn.onShutdown()
        ircConn.onShutdown()

        logger.info("Bridge stopped")
        main.notifyOfBridgeShutdown(this, inErr)
    }

    internal fun persistData(json: JSONObject): JSONObject {
        json.put("statistics", statsManager.writeData(JSONObject()))
        return json
    }

    internal fun readSavedData(json: JSONObject) {
        if (json.has("statistics")) {
            statsManager.readSavedData(json.getJSONObject("statistics"))
        }
    }

    /**
     * Adds a message's handling time to the bridge's collection for monitoring purposes
     */
    fun updateStatistics(message: BridgeMessage, sendInstant: Instant) {
        statsManager.processMessage(message, sendInstant)
    }
}
