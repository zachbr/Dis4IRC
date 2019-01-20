/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelPartEvent

class IrcJoinQuitListener(private val pier: IrcPier) {
    private val logger = pier.logger

    @Handler
    fun onUserJoinChan(event: ChannelJoinEvent) {
        // don't log our own joins
        if (event.user.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC JOIN ${event.channel.name}  ${event.user.nick}")

        val sender = BOT_SENDER
        val source = event.channel.asBridgeSource()
        val msgContent = "${event.user.nick} (${event.user.userString}@${event.user.host}) has joined ${event.channel.name}"
        val message = Message(msgContent, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }

    @Handler
    fun onUserLeaveChan(event: ChannelPartEvent) {
        // don't log our own leaving
        if (event.user.nick == pier.getBotNick()) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC PART ${event.channel.name}  ${event.user.nick}")

        val sender = BOT_SENDER
        val source = event.channel.asBridgeSource()
        val msgContent = "${event.user.nick} (${event.user.userString}@${event.user.host}) has left ${event.channel.name}"
        val message = Message(msgContent, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
