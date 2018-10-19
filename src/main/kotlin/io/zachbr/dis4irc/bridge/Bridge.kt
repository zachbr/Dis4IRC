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

import io.zachbr.dis4irc.command.CommandManager
import io.zachbr.dis4irc.command.api.Sender
import io.zachbr.dis4irc.command.api.SimpleCommand
import io.zachbr.dis4irc.command.api.Source
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.MessageChannel
import net.dv8tion.jda.core.entities.User
import org.kitteh.irc.client.library.Client
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

    private var discordApi: JDA? = null
    private var ircConn: Client? = null

    /**
     * Connects to IRC and Discord
     */
    fun startBridge() {
        logger.debug(config.toString())

        try {
            initDiscordConnection()
            initIrcConnection()
        } catch (ex: IOException) {
            logger.error("IO Exception while initializing connections: $ex")
            ex.printStackTrace()
        } catch (ex: IllegalArgumentException) {
            logger.error("Argument Exception while initializing connections: $ex")
            ex.printStackTrace()
        }
    }

    /**
     * Prepares and connects to Discord API
     */
    private fun initDiscordConnection() {
        logger.info("Connecting to Discord API...")

        discordApi = JDABuilder()
            .setToken(config.discordApiKey)
            .setGame(Game.of(Game.GameType.DEFAULT, "IRC"))
            .addEventListener(DiscordListener(this))
            .build()
            .awaitReady()

        logger.info("Discord Bot Invite URL: ${discordApi?.asBot()?.getInviteUrl()}")
        logger.info("Connected to Discord!")
    }

    /**
     * Prepares and connects the IRC bot to the server
     */
    private fun initIrcConnection() {
        logger.info("Connecting to IRC Server")

        ircConn = Client.builder()
            .nick(config.ircNickName)
            .serverHost(config.ircHostname)
            .serverPort(config.ircPort)
            .serverPassword(config.ircPassword)
            .secure(config.ircSslEnabled)
            .user(config.ircUserName)
            .realName(config.ircRealName)
            .buildAndConnect()

        for (mapping in config.channelMappings) {
            ircConn?.addChannel(mapping.ircChannel)
            logger.debug("Joined ${mapping.ircChannel}")
        }

        ircConn?.eventManager?.registerEventListener(IRCListener(this))

        logger.info("Connected to IRC!")
    }

    /**
     * Process a message received from Discord
     */
    internal fun handleMessageFromDiscord(sender: User, channel: MessageChannel, msg: String) {
        val to = channelMappings.getMappingFor(channel)

        if (to == null) {
            logger.debug("Discarding message - Discord ${channel.name} does not have a IRC channel mapping!")
            return
        }

        sendRawIrcMessage(to, "<${sender.name}> $msg")

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
    internal fun handleMessageFromIrc(sender: org.kitteh.irc.client.library.element.User, channel: Channel, msg: String) {
        val to = channelMappings.getMappingFor(channel)

        if (to == null) {
            logger.debug("Discarding message - IRC ${channel.name} does not have a Discord channel mapping!")
            return
        }

        sendRawDiscordMessage(to, "<${sender.nick}> $msg")

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
                sendRawIrcMessage(target, line)
            }
        }

        if (result.shouldSendToDiscord()) {
            val target = if (result.source == Source.DISCORD) { result.channel } else { bridgeTarget }
            sendRawDiscordMessage(target, output)
        }

    }

    /**
     * Gets the Discord bot's user id or 0 if it hasn't been initialized
     */
    internal fun getDiscordBotId(): Long = if (discordApi == null) { 0 } else { discordApi!!.selfUser.idLong }

    /**
     * Gets the IRC bot's nickname or empty string if it hasn't been initialized
     */
    internal fun getIrcBotNick(): String = if (ircConn == null) { "" } else { ircConn!!.name }

    /**
     * Clean up and disconnect from the IRC and Discord platforms
     */
    internal fun shutdown() {
        logger.info("Stopping...")

        discordApi?.shutdownNow()
        ircConn?.shutdown("Exiting...")

        logger.info("${config.bridgeName} stopped")
    }

    /**
     * Sends the given message directly to the specified IRC channel
     */
    private fun sendRawIrcMessage(channel: String, msg: String) {
        val ircChannel = ircConn?.getChannel(channel)
        if (ircChannel == null) {
            logger.error("Got null IRC channel back from IRC API!")
            Throwable().printStackTrace()
            return
        }

        if (!ircChannel.isPresent) {
            logger.warn("Bridge is not present in IRC channel $channel")
            return
        }

        ircChannel.get().sendMessage(msg)
    }

    /**
     * Sends the given message directly to the specified Discord channel
     */
    private fun sendRawDiscordMessage(channel: String, msg: String) {
        val discordChannel = discordApi?.getTextChannelById(channel)
        if (discordChannel == null) {
            logger.warn("Bridge is not present in Discord channel $channel!")
            return
        }

        if (!discordChannel.canTalk()) {
            logger.warn("Bridge cannot speak in ${discordChannel.name} to send message: $msg")
            return
        }

        discordChannel.sendMessage(msg).complete()
    }
}
