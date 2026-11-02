/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.capabilities.CapabilitiesSupportedListEvent
import org.kitteh.irc.client.library.feature.CapabilityManager

/**
 * Requests the IRCv3 capabilities needed for threaded replies that the IRC library doesn't request by default
 */
class IrcCapabilityListener {
    @Handler
    fun onCapabilitiesSupported(event: CapabilitiesSupportedListEvent) {
        val echoMessage = CapabilityManager.Defaults.ECHO_MESSAGE
        if (event.supportedCapabilities.any { it.name == echoMessage }) {
            event.addRequest(echoMessage)
        }
    }
}
