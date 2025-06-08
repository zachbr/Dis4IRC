/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.CommandMessage
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.IrcMessage
import io.zachbr.dis4irc.util.WrappingLongArray
import org.json.JSONObject
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Responsible for keeping and providing various statistics for the bridge
 */
class StatisticsManager(private val bridge: Bridge) {
    private val messageTimings = WrappingLongArray(1000)
    private var totalFromIrc = BigInteger.valueOf(0)
    private var totalFromDiscord = BigInteger.valueOf(0)

    /**
     * Processes a message, adding it to whatever statistic counters it needs
     */
    fun processMessage(bMessage: BridgeMessage, sentInstant: Instant) {
        // don't count bot, command, etc messages
        if (bMessage.originatesFromBridgeItself()) {
            return
        }

        val message = bMessage.message
        when (message) {
            is DiscordMessage -> totalFromDiscord++
            is IrcMessage -> totalFromIrc++
            is CommandMessage -> return // command messages originate on bridge, don't need to count
        }

        val difference = Duration.between(message.timestamp, sentInstant).toNanos()
        messageTimings.add(difference)
        bridge.logger.debug("Message from ${message.source.channelName} ${message.sender.displayName} took ${TimeUnit.NANOSECONDS.toMillis(difference)}ms to handle")
    }

    /**
     * Gets the total count of messages sent from IRC since the bridge was started
     */
    fun getTotalFromIrc(): BigInteger {
        return totalFromIrc
    }

    /**
     * Gets the total count of messages sent from Discord since the bridge was started
     */
    fun getTotalFromDiscord(): BigInteger {
        return totalFromDiscord
    }

    /**
     * Gets an array containing the unsorted message
     */
    fun getMessageTimings(): LongArray {
        return messageTimings.toLongArray()
    }

    fun writeData(json: JSONObject): JSONObject {
        json.put("irc", totalFromIrc)
        json.put("discord", totalFromDiscord)
        return json
    }

    fun readSavedData(json: JSONObject) {
        val ircLoaded: BigInteger = json.optBigInteger("irc", BigInteger.ZERO)
        val discordLoaded: BigInteger = json.optBigInteger("discord", BigInteger.ZERO)
        totalFromIrc += ircLoaded
        totalFromDiscord += discordLoaded
    }
}
