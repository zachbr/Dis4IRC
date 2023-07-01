/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2023 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.pier.Pier
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.Client.Builder.Server.SecurityType
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.util.Format
import org.slf4j.Logger
import java.util.regex.Pattern
import kotlin.math.abs

const val ANTI_PING_CHAR = 0x200B.toChar() // zero width space
private val NICK_COLORS = arrayOf("10", "06", "03", "07", "12", "11", "13", "09", "02")

class IrcPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private val messageCutter = EncodedMessageCutter(Charsets.UTF_8)
    private lateinit var ircConn: Client
    private var antiPing: Boolean = false
    private var noPrefix: Pattern? = null
    private var referenceLengthLimit: Int = 90

    override fun start() {
        logger.info("Connecting to IRC Server")

        // configure IRC
        val builder = Client.builder()
            .nick(bridge.config.irc.nickName)
            .user(bridge.config.irc.userName)
            .realName(bridge.config.irc.realName)
            .server()
                .host(bridge.config.irc.hostname)
                .port(bridge.config.irc.port, if (bridge.config.irc.sslEnabled) SecurityType.SECURE else SecurityType.INSECURE)
                .password(bridge.config.irc.password)

        if (bridge.config.irc.allowInvalidCerts) {
            logger.warn("Allowing invalid TLS certificates for IRC. This is not recommended.")
            builder.secureTrustManagerFactory(InsecureTrustManagerFactory.INSTANCE)
        }

        // connect
        ircConn = builder.then().buildAndConnect()
        ircConn.client.exceptionListener.setConsumer { bridge.logger.error("Exception from IRC API: ${it.localizedMessage}")}

        // listeners
        ircConn.eventManager.registerEventListener(IrcConnectionListener(this))
        ircConn.eventManager.registerEventListener(IrcMessageListener(this))

        if (bridge.config.announceJoinsQuits) {
            ircConn.eventManager.registerEventListener(IrcJoinQuitListener(this))
        }

        if (bridge.config.announceExtras) {
            ircConn.eventManager.registerEventListener(IrcExtrasListener(this))
        }

        noPrefix = bridge.config.irc.noPrefixRegex
        antiPing = bridge.config.irc.antiPing
        referenceLengthLimit = bridge.config.irc.discordReplyContextLimit
    }

    override fun onShutdown() {
        if (this::ircConn.isInitialized) {
            ircConn.shutdown("Exiting...")
        }
    }

    override fun sendMessage(targetChan: String, msg: Message) {
        if (!this::ircConn.isInitialized) {
            logger.error("IRC Connection has not been initialized yet!")
            return
        }

        val channel = getChannelByName(targetChan)
        if (channel == null) {
            logger.error("Unable to get or join $targetChan to send message from ${msg.sender.displayName}")
            logger.debug(msg.toString())
            return
        }

        var msgContent = msg.contents

        if (msg.attachments != null && msg.attachments.isNotEmpty()) {
            msg.attachments.forEach { msgContent += " $it"}
        }

        // discord reply handling
        if (msg.referencedMessage != null && referenceLengthLimit > 0) {
            var context = msg.referencedMessage.contents.replace("\n", " ") // no newlines in context msgs
            if (context.length > referenceLengthLimit) {
                context = context.substring(0, referenceLengthLimit - 1) + "..."
            }

            val refSender = createMessagePrefix(msg.referencedMessage, withAsciiAngleBracket = false)
            channel.sendMessage("Reply to \"$refSender: $context\"")
        }

        val messagePrefix = createMessagePrefix(msg)
        val noPrefixPattern = noPrefix
        val msgLines = msgContent.split("\n")
        for (line in msgLines) {
            var ircMsgOut = line

            if (noPrefixPattern == null || !noPrefixPattern.matcher(ircMsgOut).find()) {
                ircMsgOut = "$messagePrefix $line"
            } else {
                logger.debug("Message matches no-prefix-regex: $noPrefixPattern, sending without name")
                if (bridge.config.irc.announceForwardedCommands) {
                    channel.sendMessage("Forwarded command from ${createMessagePrefix(msg, withAsciiAngleBracket = false)}")
                }
            }

            channel.sendMultiLineMessage(ircMsgOut, messageCutter)
        }

        val outTimestamp = System.nanoTime()
        bridge.updateStatistics(msg, outTimestamp)
    }

    fun sendNotice(targetUser: String, message: String) {
        if (!this::ircConn.isInitialized) {
            logger.error("IRC Connection has not been initialized yet!")
            return
        }

        for (line in message.split("\n")) {
            ircConn.sendMultiLineNotice(targetUser, line, messageCutter)
        }
    }

    fun createMessagePrefix(msg: Message, withAsciiAngleBracket: Boolean = true): String {
        if (msg.originatesFromBridgeItself()) {
            return ""
        }

        var nameOut = msg.sender.displayName
        if (antiPing) {
            nameOut = rebuildWithAntiPing(nameOut)
        }

        if (bridge.config.irc.useNickNameColor) {
            val color = getColorCodeForName(msg.sender.displayName)
            nameOut = Format.COLOR_CHAR + color + nameOut + Format.RESET
        }

        return if (withAsciiAngleBracket) {
            "<$nameOut>"
        } else {
            nameOut
        }
    }

    // https://github.com/korobi/Web/blob/master/src/Korobi/WebBundle/IRC/Parser/NickColours.php
    private fun getColorCodeForName(nick: String): String {
        var index = 0
        nick.toCharArray().forEach { index += it.code.toByte() }
        return NICK_COLORS[abs(index) % NICK_COLORS.size]
    }

    /**
     * Gets a channel by name, joining it if necessary
     */
    private fun getChannelByName(name: String): Channel? {
        val chan = ircConn.getChannel(name).toNullable()
        if (chan == null) {
            logger.warn("Bridge not in expected channel $name, was it kicked?")
            logger.debug("Attempting to rejoin $name")
            ircConn.addChannel(name)
        }

        return chan ?: ircConn.getChannel(name).toNullable()
    }

    /**
     * Gets the IRC bot user's nickname
     */
    fun getBotNick(): String {
        return ircConn.nick
    }

    /**
     * Sends a message to the bridge for processing
     */
    fun sendToBridge(message: Message) {
        bridge.submitMessage(message)
    }

    /**
     * Signals the bridge that the pier needs to shutdown
     */
    fun signalShutdown(inErr: Boolean) {
        this.bridge.shutdown(inErr)
    }

    /**
     * Handle tasks that need to be done after connection but before the bot can start being used
     */
    fun runPostConnectTasks() {
        // execute any startup commands
        for (command in bridge.config.irc.startupRawCommands) {
            logger.debug("Sending raw init command: $command")
            ircConn.sendRawLine(command)
        }

        // join all mapped channels
        for (mapping in bridge.config.channelMappings) {
            logger.debug("Joining ${mapping.ircChannel}")
            ircConn.addChannel(mapping.ircChannel)
        }
    }
}

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
