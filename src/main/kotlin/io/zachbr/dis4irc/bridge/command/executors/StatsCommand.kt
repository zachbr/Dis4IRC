/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.command.executors

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.command.api.Executor
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import java.lang.management.ManagementFactory
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val EXEC_DELAY_MILLIS = 60_000

class StatsCommand(private val bridge: Bridge) : Executor {
    private val percentageContext = MathContext(4, RoundingMode.HALF_UP)
    private var lastExecution = AtomicLong(0L)

    private fun isRateLimited(): Boolean {
        val now = System.currentTimeMillis()
        val last = lastExecution.get()

        if (now - last > EXEC_DELAY_MILLIS) {
            if (lastExecution.compareAndSet(last, now)) {
                return false
            }
        }
        return true
    }

    override fun onCommand(command: PlatformMessage): String? {
        if (isRateLimited()) {
            return null
        }

        val sortedTimings = bridge.statsManager.getMessageTimings().apply { sort() } // avoid copy
        val meanMillis = TimeUnit.NANOSECONDS.toMillis(mean(sortedTimings))
        val medianMillis = TimeUnit.NANOSECONDS.toMillis(median(sortedTimings))

        val uptime = ManagementFactory.getRuntimeMXBean().uptime
        val uptimeStr = convertMillisToPretty(uptime)

        val fromIrc = bridge.statsManager.getTotalFromIrc()
        val fromDiscord = bridge.statsManager.getTotalFromDiscord()

        val fromIrcPercent = percent(fromIrc, fromDiscord + fromIrc)
        val fromDiscordPercent = BigDecimal.valueOf(100).subtract(fromIrcPercent, percentageContext)

        return "Uptime: $uptimeStr\n" +
                "Message Handling: ${meanMillis}ms / ${medianMillis}ms (mean/median)\n" +
                "Messages from IRC: $fromIrc (${fromIrcPercent.toDouble()}%)\n" +
                "Messages from Discord: $fromDiscord (${fromDiscordPercent.toDouble()}%)"
    }

    private fun percent(value: BigInteger, total: BigInteger): BigDecimal {
        if (total == BigInteger.ZERO || value == total) {
            return BigDecimal.valueOf(100)
        }

        return BigDecimal(value)
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal(total), percentageContext)
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

        return "$elapsedDays days, $elapsedHours hours, $elapsedMinutes minutes, $elapsedSeconds seconds"
    }
}
