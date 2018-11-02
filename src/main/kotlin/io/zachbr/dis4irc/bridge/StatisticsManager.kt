/*
 * This file is part of Dis4IRC.
 *
 * Dis4IRC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dis4IRC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Dis4IRC.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.zachbr.dis4irc.bridge

import com.google.common.collect.EvictingQueue
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.Source
import java.util.concurrent.TimeUnit

/**
 * Responsible for keeping and providing various statistics for the bridge
 */
class StatisticsManager(private val bridge: Bridge) {
    private val messageTimings = EvictingQueue.create<Long>(500)
    private var totalFromIrc: Long = 0
    private var totalFromDiscord: Long = 0

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
    fun getTotalFromIrc(): Long {
        return totalFromIrc
    }

    /**
     * Gets the total count of messages sent from Discord since the bridge was started
     */
    fun getTotalFromDiscord(): Long {
        return totalFromDiscord
    }

    /**
     * Gets an array containing the unsorted message
     */
    fun getMessageTimings(): LongArray {
        return messageTimings.toLongArray()
    }
}
