/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2020 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import com.google.common.collect.EvictingQueue
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/**
 * Responsible for keeping and providing various statistics for the bridge
 */
class StatisticsManager(private val bridge: Bridge) {
    private val messageTimings = EvictingQueue.create<Long>(1000)
    private var totalFromIrc = BigInteger.valueOf(0)
    private var totalFromDiscord = BigInteger.valueOf(0)

    /**
     * Processes a message, adding it to whatever statistic counters it needs
     */
    fun processMessage(message: Message, nanoTimestamp: Long) {
        // don't count bot, command, etc messages
        if (message.originatesFromBridgeItself()) {
            return
        }

        when (message.source.type) {
            PlatformType.DISCORD -> totalFromDiscord++
            PlatformType.IRC -> totalFromIrc++
        }

        val difference = nanoTimestamp - message.timestamp
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
