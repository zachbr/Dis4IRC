package io.zachbr.dis4irc.bridge

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

class IRCListener(private val bridge: Bridge) {
    val logger = bridge.logger

    @Handler
    public fun onUserJoinChan(event: ChannelJoinEvent) {
        logger.debug("IRC JOIN " + event.channel.name + " " + event.actor.nick)
    }

    @Handler
    public fun onMessage(event: ChannelMessageEvent) {
        if (event.actor.nick == bridge.ircConn?.nick) {
            return
        }

        logger.debug("IRC " + event.channel.name + " " + event.actor.nick + ": " + event.message)
        bridge.bridgeToDiscord(event.actor.nick, event.channel, event.message)
    }
}
