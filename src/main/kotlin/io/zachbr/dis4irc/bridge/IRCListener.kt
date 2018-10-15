package io.zachbr.dis4irc.bridge

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

class IRCListener(private val bridge: Bridge) {
    private val logger = bridge.logger

    @Handler
    fun onUserJoinChan(event: ChannelJoinEvent) {
        logger.debug("IRC JOIN " + event.channel.name + " " + event.actor.nick)
    }

    @Handler
    fun onMessage(event: ChannelMessageEvent) {
        // dont bridge itself
        if (event.actor.nick == bridge.getIRCBotNick()) {
            return
        }

        // dont bridge unrelated channels
        if (!bridge.hasMappingFor(event.channel)) {
            return
        }

        logger.debug("IRC " + event.channel.name + " " + event.actor.nick + ": " + event.message)
        bridge.bridgeToDiscord(event.actor.nick, event.channel, event.message)
    }
}
