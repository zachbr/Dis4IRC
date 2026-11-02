/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.message.IrcMessage
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelCtcpEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent
import org.kitteh.irc.client.library.event.helper.ServerMessageEvent
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import org.kitteh.irc.client.library.event.user.PrivateNoticeEvent
import org.kitteh.irc.client.library.util.Format
import java.time.Instant

private const val CTCP_ACTION = "ACTION"

private const val MSGID_TAG = "msgid"
// +reply is the name the tag will have once ratified, some clients send both
private val REPLY_TAGS = listOf("+draft/reply", "+reply")

/**
 * Gets the IRCv3 msgid tag of the message behind this event, if the server sent one
 */
private fun ServerMessageEvent.msgId(): String? = source.getTag(MSGID_TAG).flatMap { it.value }.orElse(null)

/**
 * Gets the msgid this message replies to (IRCv3 reply client tag), if any
 */
private fun ServerMessageEvent.replyToMsgId(): String? = replyToMsgId { source.getTag(it).flatMap { tag -> tag.value }.orElse(null) }

internal fun replyToMsgId(getTag: (String) -> String?): String? = REPLY_TAGS.firstNotNullOfOrNull { getTag(it)?.takeIf(String::isNotEmpty) }

/**
 * Responsible for listening to incoming IRC messages and filtering garbage
 */
class IrcMessageListener(private val pier: IrcPier) {
    private val logger = pier.logger

    @Handler
    fun onMessage(event: ChannelMessageEvent) {
        // ignore messages sent by this bot, other than noting the msgid of echoes
        if (event.actor.nick == pier.getBotNick()) {
            pier.onOwnMessageEcho(event.channel.name, event.message, event.msgId())
            return
        }

        val receiveInstant = Instant.now()
        logger.debug("IRC MSG ${event.channel.name} ${event.actor.nick}: ${event.message}")

        val sender = event.actor.asBridgeSender()
        val source = event.channel.asBridgeSource()
        val message = IrcMessage(event.message, sender, source, receiveInstant, event.msgId(), event.replyToMsgId())
        pier.sendToBridge(message)
    }

    @Handler
    fun onChannelCtcp(event: ChannelCtcpEvent) {
        // ignore messages sent by this bot
        if (event.actor.nick == pier.getBotNick()) {
            return
        }

        val receveInstant = Instant.now()
        logger.debug("IRC CTCP ${event.channel.name} ${event.actor.nick}: ${event.message}")

        // if it's not an action we probably don't care
        if (!event.message.startsWith(CTCP_ACTION)) {
            return
        }

        // add italic code and strip off the ctcp type info
        val messageText = "${Format.ITALIC}${event.message.substring(CTCP_ACTION.length + 1)}"

        val sender = event.actor.asBridgeSender()
        val source = event.channel.asBridgeSource()
        val message = IrcMessage(messageText, sender, source, receveInstant, event.msgId(), event.replyToMsgId())
        pier.sendToBridge(message)
    }

    @Handler
    fun onPrivateMessage(event: PrivateMessageEvent) {
        logger.debug("IRC PRIVMSG ${event.actor.nick}: ${event.message}")
    }

    @Handler
    fun onPrivateNotice(event: PrivateNoticeEvent) {
        logger.debug("IRC NOTICE ${event.actor.nick}: ${event.message}")
    }
}
