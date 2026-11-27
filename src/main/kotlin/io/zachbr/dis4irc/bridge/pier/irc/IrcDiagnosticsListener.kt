/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinFailureEvent
import org.kitteh.irc.client.library.event.client.ClientReceiveErrorEvent
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent
import org.kitteh.irc.client.library.feature.filter.NumericFilter

class IrcDiagnosticsListener(private val pier: IrcPier) {
    private val logger = pier.logger

    @Handler
    fun onJoinFailure(event: ChannelJoinFailureEvent) {
        val retry = if (event.isRemovedFromIntended) " Will not retry." else ""
        logger.warn("Unable to join IRC channel ${event.channelName}: ${event.reason} (${event.message}).$retry")
    }

    @NumericFilter(437)
    @NumericFilter(477)
    @Handler
    fun onAmbiguousJoinReply(event: ClientReceiveNumericEvent) {
        logger.warn("IRC server replied ${event.numeric}: ${event.parameters.drop(1).joinToString(" ")}")
    }

    @Handler
    fun onServerError(event: ClientReceiveErrorEvent) {
        logger.warn("IRC server sent ERROR: ${event.message}")
    }
}
