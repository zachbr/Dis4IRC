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

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.bridge.command.COMMAND_PREFIX
import io.zachbr.dis4irc.bridge.command.BOT_SENDER
import io.zachbr.dis4irc.bridge.command.CommandManager
import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.mutator.MutatorManager
import io.zachbr.dis4irc.bridge.pier.Pier
import io.zachbr.dis4irc.bridge.pier.discord.DiscordPier
import io.zachbr.dis4irc.bridge.pier.irc.IRCPier
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * Responsible for the connection between Discord and IRC, including message processing hand offs
 */
class Bridge(private val config: BridgeConfiguration) {
    internal val logger = LoggerFactory.getLogger(config.bridgeName) ?: throw IllegalStateException("Could not init logger")
    private val channelMappings = ChannelMappingManager(config)
    private val commandManager = CommandManager(this)
    private val mutatorManager = MutatorManager()

    private val discordConn: Pier
    private val ircConn: Pier

    init {
        discordConn = DiscordPier(this)
        ircConn = IRCPier(this)
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
    internal fun handleMessage(message: Message) {
        val bridgeTarget: String? = channelMappings.getMappingFor(message.channel)

        if (bridgeTarget == null) {
            logger.debug("Discarding message with no bridge target from: ${message.channel}")
            return
        }

        // mutate message contents
        message.contents = mutatorManager.applyMutators(message) ?: return

        if (message.shouldSendToIrc()) {
            val target: String = if (message.channel.type == Channel.Type.IRC) message.channel.name else bridgeTarget
            ircConn.sendMessage(target, message)
        }

        if (message.shouldSendToDiscord()) {
            val target = if (message.channel.type == Channel.Type.DISCORD) message.channel.discordId.toString() else bridgeTarget
            discordConn.sendMessage(target, message)
        }

        // command handling
        if (message.contents.startsWith(COMMAND_PREFIX) && message.sender != BOT_SENDER) {
            commandManager.processCommandMessage(message)
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
}
