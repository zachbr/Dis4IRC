/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.MessageIdCache
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class IrcReplyTagsTest {
    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    @Test
    fun testEchoMatchesInOrder() {
        val tracker = IrcEchoTracker()
        tracker.expect("#test", "<a> one", 1L)
        tracker.expect("#test", "<b> two", 2L)

        assertEquals(1L, tracker.match("#test", "<a> one"))
        assertEquals(2L, tracker.match("#TEST", "<b> two"))
        assertNull(tracker.match("#test", "<b> two"))
    }

    @Test
    fun testEchoIgnoresUnknownLines() {
        val tracker = IrcEchoTracker()
        tracker.expect("#test", "<a> first part", 1L)

        assertNull(tracker.match("#test", "continuation part"))
        assertNull(tracker.match("#other", "<a> first part"))
        assertEquals(1L, tracker.match("#test", "<a> first part"))
    }

    @Test
    fun testEchoDropsSkippedEntries() {
        val tracker = IrcEchoTracker()
        tracker.expect("#test", "<a> lost", 1L)
        tracker.expect("#other", "<c> elsewhere", 3L)
        tracker.expect("#test", "<b> echoed", 2L)

        assertEquals(2L, tracker.match("#test", "<b> echoed"))
        assertNull(tracker.match("#test", "<a> lost"))
        assertEquals(3L, tracker.match("#other", "<c> elsewhere"))
    }

    @Test
    fun testEchoExpires() {
        val clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
        val tracker = IrcEchoTracker(clock)
        tracker.expect("#test", "<a> slow", 1L)

        clock.now = clock.now.plus(Duration.ofMinutes(5))
        assertNull(tracker.match("#test", "<a> slow"))
    }

    @Test
    fun testEscapeTagValue() {
        assertEquals("abc123", escapeTagValue("abc123"))
        assertEquals("a\\:b\\sc\\\\d\\r\\n", escapeTagValue("a;b c\\d\r\n"))
    }

    @Test
    fun testClientTagDeny() {
        assertFalse(isClientTagDenied(null, "draft/reply"))
        assertFalse(isClientTagDenied("", "draft/reply"))
        assertFalse(isClientTagDenied("typing", "draft/reply"))
        assertTrue(isClientTagDenied("typing,draft/reply", "draft/reply"))
        assertTrue(isClientTagDenied("*", "draft/reply"))
        assertFalse(isClientTagDenied("*,-draft/reply", "draft/reply"))
    }

    @Test
    fun testReplyTagParsing() {
        assertNull(replyToMsgId { null })
        assertEquals("abc", replyToMsgId { if (it == "+draft/reply") "abc" else null })
        assertEquals("abc", replyToMsgId { if (it == "+reply") "abc" else null })
        assertEquals("draft", replyToMsgId { if (it == "+draft/reply") "draft" else "final" })
        assertNull(replyToMsgId { "" })
    }

    @Test
    fun testMessageIdCacheBothWays() {
        val cache = MessageIdCache()
        cache.put(1L, "one")
        assertEquals("one", cache.getIrcMsgId(1L))
        assertEquals(1L, cache.getDiscordMessageId("one"))
        assertNull(cache.getDiscordMessageId("two"))
    }

    @Test
    fun testMessageIdCacheEvicts() {
        val cache = MessageIdCache(capacity = 2)
        cache.put(1L, "one")
        cache.put(2L, "two")
        assertEquals("one", cache.getIrcMsgId(1L)) // touch 1 so 2 is the eldest
        cache.put(3L, "three")

        assertEquals("one", cache.getIrcMsgId(1L))
        assertNull(cache.getIrcMsgId(2L))
        assertEquals("three", cache.getIrcMsgId(3L))
    }
}
