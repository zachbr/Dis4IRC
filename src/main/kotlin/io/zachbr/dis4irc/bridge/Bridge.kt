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

import io.zachbr.dis4irc.bridge.command.CommandManager
import io.zachbr.dis4irc.bridge.command.api.Sender
import io.zachbr.dis4irc.bridge.command.api.SimpleCommand
import io.zachbr.dis4irc.bridge.command.api.Source
import io.zachbr.dis4irc.bridge.pier.Pier
import io.zachbr.dis4irc.bridge.pier.discord.DiscordPier
import io.zachbr.dis4irc.bridge.pier.irc.IRCPier
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import org.kitteh.irc.client.library.element.Channel
import org.slf4j.LoggerFactory
import java.io.IOException

const val COMMAND_PREFIX: String = "!"

/**
 * Responsible for the connection between Discord and IRC, including message processing hand offs
 */
class Bridge(private val config: BridgeConfiguration) {
    internal val logger = LoggerFactory.getLogger(config.bridgeName) ?: throw IllegalStateException("Could not init logger")
    private val channelMappings = ChannelMappingManager(config)
    private val commandManager = CommandManager(this)

    private val discordConn: Pier
    private val ircConn: Pier

    init {
        // todo - discord webhooks
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
            logger.error("IO Exception while initializing connections: $ex")
            ex.printStackTrace()
        } catch (ex: IllegalArgumentException) {
            logger.error("Argument Exception while initializing connections: $ex")
            ex.printStackTrace()
        }
    }

    /**
     * Process a message received from Discord
     */
    internal fun handleMessageFromDiscord(sender: User, channel: MessageChannel, msg: String) { // todo - generify, remove specific handler
        val to = channelMappings.getMappingFor(channel)

        if (to == null) {
            logger.debug("Discarding message - Discord ${channel.name} does not have a IRC channel mapping!")
            return
        }

        ircConn.sendMessage(to, "<${sender.name}> $msg")

        if (msg.startsWith(COMMAND_PREFIX)) {
            logger.debug("Handling message as command")
            val senderObj = Sender(sender.name, sender.idLong, null)
            val command = SimpleCommand(msg, senderObj, channel.id, Source.DISCORD, this)
            commandManager.processCommandMessage(command)
        }
    }

    /**
     * Process a message received from IRC
     */
    internal fun handleMessageFromIrc(sender: org.kitteh.irc.client.library.element.User, channel: Channel, msg: String) { // todo - generify, remove specific handler
        val to = channelMappings.getMappingFor(channel)

        if (to == null) {
            logger.debug("Discarding message - IRC ${channel.name} does not have a Discord channel mapping!")
            return
        }

        discordConn.sendMessage(to, "<${sender.nick}> $msg")

        if (msg.startsWith(COMMAND_PREFIX)) {
            logger.debug("Handling message as command")

            var nickServAcct: String? = null
            if (sender.account.isPresent) {
                nickServAcct = sender.account.get()
            }

            val senderObj = Sender(sender.nick, null, nickServAcct)
            val command = SimpleCommand(msg, senderObj, channel.name, Source.IRC, this)
            commandManager.processCommandMessage(command)
        }
    }

    /**
     * Process a command executor's submission
     */
    internal fun handleCommand(result: SimpleCommand, output: String) {
        val bridgeTarget: String? = when {
            result.source == Source.DISCORD -> channelMappings.getMappingForDiscordChannelBy(result.channel)
            result.source == Source.IRC -> channelMappings.getMappingForIrcChannelByName(result.channel)
            else -> return
        }

        if (bridgeTarget == null) {
            logger.warn("Command result handling didn't return early for tertiary source!")
            return
        }

        if (result.shouldSendToIrc()) {
            val out = output.split("\n")
            val target = if (result.source == Source.IRC) { result.channel } else { bridgeTarget }
            for (line in out) {
                ircConn.sendMessage(target, line)
            }
        }

        if (result.shouldSendToDiscord()) {
            val target = if (result.source == Source.DISCORD) { result.channel } else { bridgeTarget }
            discordConn.sendMessage(target, output)
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
