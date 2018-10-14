package io.zachbr.dis4irc.bridge

import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

class DiscordListener(private val bridge: Bridge) : ListenerAdapter() {
    private val logger = bridge.logger

    override fun onMessageReceived(event: MessageReceivedEvent?) {
        // todo null check this and kill ?.
        logger.info("DISCORD " + event?.channel?.name + " " + event?.author?.name + ": " + event?.message?.contentStripped)
    }
}
