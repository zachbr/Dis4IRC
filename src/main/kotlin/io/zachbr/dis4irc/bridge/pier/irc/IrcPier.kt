/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.pier.Pier
import org.kitteh.irc.client.library.Client
import org.kitteh.irc.client.library.Client.Builder.Server.SecurityType
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.feature.CapabilityManager
import org.slf4j.Logger
import java.time.Instant
import java.util.regex.Pattern

private const val REPLY_TAG = "draft/reply"

class IrcPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private lateinit var ircConn: Client
    private var antiPing: Boolean = false
    private var noPrefix: Pattern? = null
    private var referenceLengthLimit: Int = 90
    private val echoTracker = IrcEchoTracker()

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
        ircConn.client.exceptionListener.setConsumer { bridge.logger.warn("Exception from IRC API:", it)}

        // listeners
        ircConn.eventManager.registerEventListener(IrcConnectionListener(this))
        ircConn.eventManager.registerEventListener(IrcMessageListener(this))

        if (bridge.config.irc.discordReplyTags) {
            ircConn.eventManager.registerEventListener(IrcCapabilityListener())
        }

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

    override fun sendMessage(targetChan: String, msg: BridgeMessage) {
        if (!this::ircConn.isInitialized) {
            logger.error("IRC Connection has not been initialized yet!")
            return
        }

        val channel = getChannelByName(targetChan)
        if (channel == null) {
            logger.error("Unable to get or join $targetChan to send message from ${msg.message.sender.displayName}")
            logger.debug(msg.toString())
            return
        }

        val platMessage = msg.message
        val discordMessageId = (platMessage as? DiscordMessage)?.messageId
        val replyToMsgId = (platMessage as? DiscordMessage)?.referencedMessage?.messageId
            ?.let { bridge.messageIds.getIrcMsgId(it) }
            ?.takeIf { canSendReplyTags() }
        val includeReplyContext = replyToMsgId == null || bridge.config.irc.discordReplyContextWithTags

        val ircLines = IrcMessageFormatter.format(platMessage, bridge.config, includeReplyContext)
        val tagPrefix = replyToMsgId?.let { "@+$REPLY_TAG=${escapeTagValue(it)} " } ?: ""
        val echoTrackingId = discordMessageId?.takeIf { isCapabilityEnabled(CapabilityManager.Defaults.ECHO_MESSAGE) }
        ircLines.forEachIndexed { lineIndex, line ->
            // split long lines ourselves, as sendMultiLineMessage would, so that tags can be attached to each part
            ircConn.messageCutter.split(line, getRemainingLength(channel.messagingName)).forEachIndexed { partIndex, part ->
                if (echoTrackingId != null && lineIndex == 0 && partIndex == 0) {
                    echoTracker.expect(channel.messagingName, part, echoTrackingId)
                }
                ircConn.sendRawLine("${tagPrefix}PRIVMSG ${channel.messagingName} :$part")
            }
        }

        if (ircLines.isNotEmpty()) {
            bridge.updateStatistics(msg, Instant.now())
        }
    }

    fun sendNotice(targetUser: String, message: String) {
        if (!this::ircConn.isInitialized) {
            logger.error("IRC Connection has not been initialized yet!")
            return
        }

        for (line in message.split("\n")) {
            ircConn.sendMultiLineNotice(targetUser, line)
        }
    }

    /**
     * Called when the server echoes back a message this bot sent (IRCv3 echo-message)
     */
    fun onOwnMessageEcho(channelName: String, text: String, msgId: String?) {
        if (msgId == null) {
            return
        }

        val discordMessageId = echoTracker.match(channelName, text) ?: return
        logger.debug("Discord message {} was bridged to IRC as msgid {}", discordMessageId, msgId)
        bridge.messageIds.put(discordMessageId, msgId)
    }

    /**
     * Records the Discord message an IRC message was bridged as, so Discord replies to it can be threaded on IRC
     */
    fun onBridgedToDiscord(ircMsgId: String, discordMessageId: Long) {
        logger.debug("IRC msgid {} was bridged to Discord as message {}", ircMsgId, discordMessageId)
        bridge.messageIds.put(discordMessageId, ircMsgId)
    }

    private fun isCapabilityEnabled(name: String): Boolean {
        return ircConn.capabilityManager.getCapability(name).isPresent
    }

    private fun canSendReplyTags(): Boolean {
        if (!bridge.config.irc.discordReplyTags || !isCapabilityEnabled(CapabilityManager.Defaults.MESSAGE_TAGS)) {
            return false
        }

        val clientTagDeny = ircConn.serverInfo.getISupportParameter("CLIENTTAGDENY").flatMap { it.value }.orElse(null)
        return !isClientTagDenied(clientTagDeny, REPLY_TAG)
    }

    /**
     * Space left for message content in a PRIVMSG to the target, matching the IRC library's own calculation
     */
    private fun getRemainingLength(target: String): Int {
        // :nick!name@host PRIVMSG TARGET :MESSAGE\r\n
        return 505 - ircConn.user.map { it.name.length }.orElse(100) - target.length - "PRIVMSG".length
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
    fun sendToBridge(message: PlatformMessage) {
        bridge.submitMessage(BridgeMessage(message))
    }

    /**
     * Signals the bridge that the pier needs to shut down
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
