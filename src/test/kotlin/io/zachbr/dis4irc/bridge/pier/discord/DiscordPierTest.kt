/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2022 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordPierTest {

    @Test
    fun testUsernameValidation() {
        val minimumAcceptedLength = 2
        val maximumAcceptedLength = 32

        val tooShort = "1" // length = 1
        val tooLong = "123456789012345678901234567890123" // length = 33
        val okay = "SomeUsername"

        val edgeCaseLower = "12" // length = 2
        val edgeCaseHigher = "12345678901234567890123456789012" // length = 32

        assertEquals(edgeCaseLower, enforceSenderName(edgeCaseLower))
        assertEquals(edgeCaseHigher, enforceSenderName(edgeCaseHigher))
        assertEquals(okay, enforceSenderName(okay))

        assertTrue(enforceSenderName(tooShort).length >= minimumAcceptedLength)
        assertTrue(enforceSenderName(tooLong).length <= maximumAcceptedLength)
    }
}
