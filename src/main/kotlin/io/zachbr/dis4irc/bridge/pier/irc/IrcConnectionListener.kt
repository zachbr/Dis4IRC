/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.connection.ClientConnectionClosedEvent
import org.kitteh.irc.client.library.event.connection.ClientConnectionEstablishedEvent

private const val LAST_DISCONNECT_DELTA = 30_000 // ms
private const val NUM_DISCONNECT_THRESHOLD = 4

class IrcConnectionListener(private val pier: IrcPier) {
    private val logger = pier.logger
    private var lastDisconnect = System.currentTimeMillis()
    private var numRecentDisconnects = -1

    @Handler
    fun onConnectionEstablished(event: ClientConnectionEstablishedEvent) {
        logger.info("Connected to IRC!")
        this.pier.runPostConnectTasks()
    }

    @Handler
    fun onConnectionClosed(event: ClientConnectionClosedEvent) {
        logger.warn("IRC connection closed: ${event.cause.toNullable()?.localizedMessage ?: "null reason"}")

        val now = System.currentTimeMillis()
        val shouldReconnect: Boolean
        if (now - lastDisconnect < LAST_DISCONNECT_DELTA) {
            numRecentDisconnects++

            shouldReconnect = numRecentDisconnects <= NUM_DISCONNECT_THRESHOLD
            logger.debug("Reconnect: $shouldReconnect, numRecentDisconnects: $numRecentDisconnects")
        } else {
            numRecentDisconnects = 0
            shouldReconnect = true
            logger.debug("RESET: Reconnect: $shouldReconnect, numRecentDisconnects: $numRecentDisconnects")
        }

        lastDisconnect = now
        event.setAttemptReconnect(shouldReconnect)
        if (!shouldReconnect) {
            this.pier.signalShutdown(inErr = true) // a disconnected bridge is a worthless bridge
        }
    }
}
