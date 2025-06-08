/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.message.IrcMessage
import io.zachbr.dis4irc.bridge.message.IrcSender
import net.engio.mbassy.listener.Handler
import org.kitteh.irc.client.library.event.channel.ChannelModeEvent
import org.kitteh.irc.client.library.event.channel.ChannelTopicEvent
import java.time.Instant

class IrcExtrasListener(private val pier: IrcPier) {
    private val logger = pier.logger

    @Handler
    fun onModeChange(event: ChannelModeEvent) {
        val receiveInstant = Instant.now()
        val sender = IrcSender("IRC", null)
        val msgContent = "${event.actor.name} changed channel modes: ${event.statusList.asString}"
        logger.debug("IRC MODE CHANGE {}", event.channel)

        val source = event.channel.asBridgeSource()
        val message = IrcMessage(msgContent, sender, source, receiveInstant)
        pier.sendToBridge(message)
    }

    @Handler
    fun onTopicChange(event: ChannelTopicEvent) {
        val receiveInstant = Instant.now()
        if (!event.isNew) {
            return // changes only - don't broadcast on channel join
        }

        val sender = IrcSender("IRC", null)
        val topicSetter = event.newTopic.setter.toNullable()
        val setterPrefix = if (topicSetter != null) " set by ${topicSetter.name}" else ""
        val topicValue = event.newTopic.value.orElse("Unknown topic")
        val msgContent = "Topic$setterPrefix: $topicValue"
        logger.debug("IRC TOPIC$setterPrefix: $topicValue")

        val source = event.channel.asBridgeSource()
        val message = IrcMessage(msgContent, sender, source, receiveInstant)
        pier.sendToBridge(message)
    }
}
