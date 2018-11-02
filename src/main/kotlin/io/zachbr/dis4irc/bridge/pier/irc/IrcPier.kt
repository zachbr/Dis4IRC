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

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.pier.Pier
import org.kitteh.irc.client.library.Client
import org.slf4j.Logger

private const val ANTI_PING_CHAR = 0x200B.toChar() // zero width space

class IrcPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private var antiPing: Boolean = false
    private var ircConn: Client? = null
    private var noPrefix: String? = null

    override fun init(config: BridgeConfiguration) {
        logger.info("Connecting to IRC Server")

        // configure IRC
        val builder = Client.builder()
            .nick(config.ircNickName)
            .serverHost(config.ircHostname)
            .serverPort(config.ircPort)
            .serverPassword(config.ircPassword)
            .secure(config.ircSslEnabled)
            .user(config.ircUserName)
            .realName(config.ircRealName)

        if (config.ircAllowInvalidCerts) {
            builder.secureTrustManagerFactory(null)
        }

        // connect
        ircConn = builder.buildAndConnect()

        // join all mapped channels
        for (mapping in config.channelMappings) {
            ircConn?.addChannel(mapping.ircChannel)
            logger.debug("Joined ${mapping.ircChannel}")
        }

        // execute any commands
        for (command in config.ircCommandsInit) {
            ircConn?.sendRawLine(command)
        }

        ircConn?.eventManager?.registerEventListener(IrcListener(this))
        noPrefix = config.ircNoPrefixVal
        antiPing = config.ircAntiPing

        logger.info("Connected to IRC!")
    }

    override fun shutdown() {
        ircConn?.shutdown("Exiting...")
    }

    override fun sendMessage(targetChan: String, msg: Message) {
        val ircChannel = ircConn?.getChannel(targetChan)
        if (ircChannel == null) {
            logger.error("Null optional for IRC channel!")
            Throwable().printStackTrace()
            return
        }

        if (!ircChannel.isPresent) {
            logger.warn("Bridge is not present in IRC channel $targetChan")
            return
        }

        val channel = ircChannel.get()

        var senderPrefix = "<${msg.sender.displayName}> "
        if (msg.originatesFromBridgeItself()) {
            senderPrefix = ""
        } else if (antiPing) {
            senderPrefix = StringBuilder(senderPrefix).insert(3, ANTI_PING_CHAR).toString()
        }

        var msgContent = msg.contents

        if (msg.attachments != null && msg.attachments.isNotEmpty()) {
            msg.attachments.forEach { msgContent += " $it"}
        }

        val prefixNoPrefix = noPrefix
        val msgLines = msgContent.split("\n")
        for (line in msgLines) {
            var ircMsgOut = line

            if (prefixNoPrefix == null || !line.startsWith(prefixNoPrefix)) {
                ircMsgOut = "$senderPrefix$line"
            }

            channel.sendMultiLineMessage(ircMsgOut)
        }

        val outTimestamp = System.nanoTime()
        bridge.updateStatistics(msg, outTimestamp)
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
        bridge.submitMessage(message)
    }
}
