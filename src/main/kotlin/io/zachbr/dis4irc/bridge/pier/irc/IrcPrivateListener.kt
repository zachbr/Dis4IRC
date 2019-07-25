/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.message.*
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.user.PrivateMessageEvent
import java.util.concurrent.TimeUnit

class IrcPrivateListener(private val pier: IrcPier) {
    private val logger = pier.logger
    private var pmRatelimit = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)

    @Handler
    fun onPrivateMessage(event: PrivateMessageEvent) {
        if (!event.isToClient) {
            return
        }

        if (System.currentTimeMillis() > this.pmRatelimit) {
            this.pmRatelimit = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
            event.sendReply("I am a bot, and do not respond to PMs.")
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("IRC PRIVATE MESSAGE FROM ${event.actor.name}")

        val sender = Sender(event.actor.name, null, null)
        val source = Source("private-messages", null, PlatformType.IRC)
        val message = Message(event.message, sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
