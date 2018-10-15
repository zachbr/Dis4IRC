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

import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.MessageChannel
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.element.Channel
import org.slf4j.LoggerFactory

class Bridge(private val config: BridgeConfiguration) {
    internal val logger = LoggerFactory.getLogger(config.bridgeName) ?: throw IllegalStateException("Could not init logger")
    private val discordIrcMap = HashMap<String, String>()
    private val ircDiscordMap: Map<String, String>

    internal var hasInitialized = false
    private var discordApi: JDA? = null
    private var ircConn: Client? = null

    init {
        for (mapping in config.channelMappings) {
            discordIrcMap[mapping.discordChannel] = mapping.ircChannel
        }

        ircDiscordMap = discordIrcMap.entries.associateBy({ it.value }) { it.key }
    }

    fun startBridge() {
        logger.debug(config.toString())

        try {
            initDiscordConnection()
            initIrcConnection()
        } catch (ex: IllegalArgumentException) {
            logger.error("Exception while initializing connections: $ex")
            ex.printStackTrace()
        }

        hasInitialized = true
    }

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

    internal fun toIRC(username: String, from: MessageChannel, msg: String) {
        var to = discordIrcMap[from.id]
        if (to == null) {
            to = discordIrcMap[from.name]
        }

        if (to == null) {
            logger.warn("Message from Discord ${from.name} does not have IRC channel mapping!")
            return
        }

        val ircChannel = ircConn?.getChannel(to)
        if (!ircChannel!!.isPresent) {
            logger.warn("Bridge is not present in IRC channel $to")
        }

        ircChannel.get().sendMessage("<$username> $msg")
    }

    internal fun toDiscord(username: String, from: Channel, msg: String) {
        val to = ircDiscordMap[from.name]

        if (to == null) {
            logger.warn("Message from IRC ${from.name} does not have a mapping to Discord!")
            return
        }

        val discordChannel = discordApi?.getTextChannelById(to)
        if (discordChannel == null) {
            logger.warn("Bridge is not present in Discord channel $to!")
            return
        }

        if (!discordChannel.canTalk()) {
            logger.warn("Bridge cannot speak in ${discordChannel.name} to bridge message from ${from.name}!")
            return
        }

        discordChannel.sendMessage("<$username> $msg").complete()
    }

    /**
     * Checks if the bridge has an IRC channel mapped to this Discord channel
     */
    internal fun hasMappingFor(discordChannel: MessageChannel): Boolean {
        val name = discordChannel.name
        val id = discordChannel.id

        return discordIrcMap.containsKey(id) || discordIrcMap.containsKey(name)
    }

    /**
     * Checks if the bridge has a Discord channel mapped to this IRC channel
     */
    internal fun hasMappingFor(ircChannel: Channel): Boolean {
        return ircDiscordMap.containsKey(ircChannel.name)
    }

    /**
     * Gets the Discord bot's user id or 0 if it hasn't been initialized
     */
    internal fun getDiscordBotId(): Long = if (discordApi == null) { 0 } else { discordApi!!.selfUser.idLong }

    /**
     * Gets the IRC bot's nickname or empty string if it hasn't been initialized
     */
    internal fun getIRCBotNick(): String = if (ircConn == null) { "" } else { ircConn!!.name }

    internal fun shutdown() {
        logger.info("Stopping...")

        if (discordApi != null) {
            discordApi!!.shutdownNow()
        }

        if (ircConn != null) {
            ircConn!!.shutdown("Exiting...")
        }

        logger.info("${config.bridgeName} stopped")
    }
}
