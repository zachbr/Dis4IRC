package io.zachbr.dis4irc.bridge

import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent
import org.kitteh.irc.client.library.event.channel.ChannelMessageEvent

class IRCListener(private val bridge: Bridge) {
    val logger = bridge.logger

    @Handler
    public fun onUserJoinChan(event: ChannelJoinEvent) {
        logger.info("IRC JOIN " + event.channel.name + " " + event.actor.nick)
    }

    @Handler
    public fun onMessage(event: ChannelMessageEvent) {
        logger.info("IRC " + event.channel.name + " " + event.actor.nick + ": " + event.message)
    }
}
