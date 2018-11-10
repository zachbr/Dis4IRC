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
import org.kitteh.irc.client.library.util.Format
import org.slf4j.Logger
import java.lang.StringBuilder
import java.util.regex.Pattern

private const val ANTI_PING_CHAR = 0x200B.toChar() // zero width space
private val NICK_COLORS = intArrayOf(10, 6, 3, 7, 12, 11, 13, 9, 2)

class IrcPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private var antiPing: Boolean = false
    private var ircConn: Client? = null
    private var noPrefix: Pattern? = null

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

        // listeners
        ircConn?.eventManager?.registerEventListener(IrcMessageListener(this))

        if (config.announceJoinsQuits) {
            ircConn?.eventManager?.registerEventListener(IrcJoinQuitListener(this))
        }

        noPrefix = config.ircNoPrefixRegex
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
        val senderPrefix = getSenderPrefix(msg)
        var msgContent = msg.contents

        if (msg.attachments != null && msg.attachments.isNotEmpty()) {
            msg.attachments.forEach { msgContent += " $it"}
        }

        val noPrefixPattern = noPrefix
        val msgLines = msgContent.split("\n")
        for (line in msgLines) {
            var ircMsgOut = line

            if (noPrefixPattern == null || !noPrefixPattern.matcher(ircMsgOut).find()) {
                ircMsgOut = "$senderPrefix$line"
            }

            channel.sendMultiLineMessage(ircMsgOut)
        }

        val outTimestamp = System.nanoTime()
        bridge.updateStatistics(msg, outTimestamp)
    }

    private fun getSenderPrefix(msg: Message): String {
        if (msg.originatesFromBridgeItself()) {
            return ""
        }
        return getDisplayName(msg.sender.displayName)
    }

    // https://github.com/korobi/Web/blob/master/src/Korobi/WebBundle/IRC/Parser/NickColours.php
    private fun getDisplayName(nick: String): String {
        var index = 0
        nick.toCharArray().forEach { index += it.toByte() }
        val color = NICK_COLORS[index % NICK_COLORS.size].toString()
        val newNick = if (antiPing) rebuildWithAntiPing(nick) else nick

        return "<" + Format.COLOR_CHAR + color + newNick + Format.RESET +"> "
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

    companion object {
        /**
         * Rebuilds a string with the [ANTI_PING_CHAR] character placed strategically
         */
        fun rebuildWithAntiPing(nick: String): String {
            val builder = StringBuilder()
            val length = nick.length
            for (i in nick.indices) {
                builder.append(nick[i])
                if (i + 1 >= length || !Character.isSurrogatePair(nick[i], nick[i +  1])) {
                    if (i % 2 == 0) {
                        builder.append(ANTI_PING_CHAR)
                    }
                }
            }
            return builder.toString()
        }
    }
}
