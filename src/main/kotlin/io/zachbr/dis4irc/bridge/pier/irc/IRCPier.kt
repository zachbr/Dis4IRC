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

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.api.Message
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.pier.Pier
import org.kitteh.irc.client.library.Client
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

class IRCPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private var ircConn: Client? = null

    override fun init(config: BridgeConfiguration) {
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

    override fun shutdown() {
        ircConn?.shutdown("Exiting...")
    }

    override fun sendMessage(targetChan: String, msg: Message) {
        val ircChannel = ircConn?.getChannel(targetChan)
        if (ircChannel == null) {
            logger.error("Got null IRC channel back from IRC API!")
            Throwable().printStackTrace()
            return
        }

        if (!ircChannel.isPresent) {
            logger.warn("Bridge is not present in IRC channel $targetChan")
            return
        }

        val channel = ircChannel.get()

        val out = msg.contents.split("\n")
        for (line in out) {
            channel.sendMessage("<${msg.sender.displayName}> $line")
        }

        logger.debug("Took approximately ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - msg.timestamp)}ms to handle message")
    }

    /**
     * Gets the IRC bot user's nickname
     */
    fun getBotNick(): String? {
        return ircConn?.nick
    }

    /**
     * Sends a message to the bridge for processing
     */
    fun sendToBridge(message: Message) {
        bridge.handleMessage(message)
    }
}
