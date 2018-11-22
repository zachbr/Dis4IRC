/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command.executors

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.command.api.Executor
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Sender
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

class StatsCommand(private val bridge: Bridge) : Executor {

    private fun isAuthorized(sender: Sender): Boolean {
        return true
    }

    override fun onCommand(command: Message): String? {
        if (!isAuthorized(command.sender)) {
            return null
        }

        val sortedTimings = bridge.statsManager.getMessageTimings().sortedArray()
        val meanMillis = TimeUnit.NANOSECONDS.toMillis(mean(sortedTimings))
        val medianMillis = TimeUnit.NANOSECONDS.toMillis(median(sortedTimings))

        val uptime = ManagementFactory.getRuntimeMXBean().uptime
        val uptimeStr = convertMillisToPretty(uptime)

        return "Uptime: $uptimeStr\n" +
                "Message Handling: ${meanMillis}ms / ${medianMillis}ms (mean/median)\n" +
                "Messages from IRC: ${bridge.statsManager.getTotalFromIrc()}\n" +
                "Messages from Discord: ${bridge.statsManager.getTotalFromDiscord()}"
    }

    /**
     * Gets the mean of a given array
     */
    private fun mean(a: LongArray): Long {
        var sum = 0L
        for (i in a.indices) {
            sum += a[i]
        }

        return sum / a.size
    }

    /**
     * Gets the median of a given sorted array
     */
    private fun median(a: LongArray): Long {
        val middle = a.size / 2

        return if (a.size % 2 == 1) {
            a[middle]
        } else {
            (a[middle - 1] + a[middle]) / 2
        }
    }

    /**
     * Converts the given amount of milliseconds to a presentable elapsed time string
     */
    private fun convertMillisToPretty(diffMillis: Long): String {
        var left = diffMillis

        val secondsInMilli: Long = 1000
        val minutesInMilli = secondsInMilli * 60
        val hoursInMilli = minutesInMilli * 60
        val daysInMilli = hoursInMilli * 24

        val elapsedDays = left / daysInMilli
        left %= daysInMilli

        val elapsedHours = left / hoursInMilli
        left %= hoursInMilli

        val elapsedMinutes = left / minutesInMilli
        left %= minutesInMilli

        val elapsedSeconds = left / secondsInMilli

        return String.format("%d days, %d hours, %d minutes, %d seconds",
            elapsedDays, elapsedHours, elapsedMinutes, elapsedSeconds)
    }
}
