/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IrcPierTest {
    @Test
    fun testAntiPing() {
        assertEquals("k\u200Bit\u200Bte\u200Bn", IrcMessageFormatter.rebuildWithAntiPing("kitten"))
        assertEquals("k\u200Bit\u200Bte\u200Bn \u200B\uD83C\uDF57\u200B", IrcMessageFormatter.rebuildWithAntiPing("kitten \uD83C\uDF57"))
    }
}
