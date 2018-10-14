package io.zachbr.dis4irc.bridge

import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

class DiscordListener(private val bridge: Bridge) : ListenerAdapter() {
    private val logger = bridge.logger

    override fun onMessageReceived(event: MessageReceivedEvent?) {
        if (event == null) {
            logger.debug("Null Discord event from JDA")
            return
        }

        if (event.author.idLong == bridge.discordApi?.selfUser?.idLong) {
            return
        }

        logger.debug("DISCORD " + event.channel?.name + " " + event.author.name + ": " + event.message.contentStripped)
        bridge.bridgeToIRC(event.author.name, event.channel, event.message.contentStripped)
    }
}
