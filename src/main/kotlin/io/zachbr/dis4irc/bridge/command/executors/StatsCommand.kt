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
                "Count from IRC: ${bridge.statsManager.getTotalFromIrc()}\n" +
                "Count from Discord: ${bridge.statsManager.getTotalFromDiscord()}"
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
