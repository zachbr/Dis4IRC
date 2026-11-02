/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import java.time.Clock
import java.time.Duration
import java.time.Instant

private const val MAX_PENDING = 256
private val PENDING_TIMEOUT: Duration = Duration.ofSeconds(60)

/**
 * Matches the server's echoes (IRCv3 echo-message) of lines the bridge sent back to the Discord message that
 * produced them, so the msgid the server assigned can be recorded.
 *
 * The server echoes lines in the order we sent them, so matching is done in queue order on channel and exact text.
 * Anything that doesn't match (continuation lines, lines the server altered) is ignored.
 */
class IrcEchoTracker(private val clock: Clock = Clock.systemUTC()) {
    private data class Pending(val channel: String, val text: String, val discordMessageId: Long, val queuedAt: Instant)

    private val pending = ArrayDeque<Pending>()

    @Synchronized
    fun expect(channel: String, text: String, discordMessageId: Long) {
        if (pending.size >= MAX_PENDING) {
            pending.removeFirst()
        }
        pending.addLast(Pending(channel, text, discordMessageId, clock.instant()))
    }

    /**
     * Returns the Discord message id the echoed line belongs to, or null if it isn't one we are waiting on.
     */
    @Synchronized
    fun match(channel: String, text: String): Long? {
        val cutoff = clock.instant().minus(PENDING_TIMEOUT)
        pending.removeAll { it.queuedAt.isBefore(cutoff) }

        val index = pending.indexOfFirst { it.channel.equals(channel, ignoreCase = true) && it.text == text }
        if (index == -1) {
            return null
        }

        val match = pending[index]
        // anything queued before the match in the same channel was never echoed as sent, drop it
        val iterator = pending.iterator()
        var i = 0
        while (iterator.hasNext() && i <= index) {
            if (iterator.next().channel.equals(channel, ignoreCase = true)) {
                iterator.remove()
            }
            i++
        }

        return match.discordMessageId
    }
}

/**
 * Escapes a value for use in an IRCv3 message tag.
 */
fun escapeTagValue(value: String): String {
    val builder = StringBuilder(value.length)
    for (char in value) {
        when (char) {
            ';' -> builder.append("\\:")
            ' ' -> builder.append("\\s")
            '\\' -> builder.append("\\\\")
            '\r' -> builder.append("\\r")
            '\n' -> builder.append("\\n")
            else -> builder.append(char)
        }
    }
    return builder.toString()
}

/**
 * Checks a CLIENTTAGDENY ISUPPORT value to see whether the given client tag (without the + prefix) is blocked.
 * See https://ircv3.net/specs/extensions/message-tags#rpl_isupport-tokens
 */
fun isClientTagDenied(clientTagDeny: String?, tag: String): Boolean {
    if (clientTagDeny.isNullOrBlank()) {
        return false
    }

    val entries = clientTagDeny.split(',')
    if ("*" in entries) {
        return "-$tag" !in entries
    }
    return tag in entries
}
