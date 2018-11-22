/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiscordPierTest {
    @Test
    fun testReplaceTarget() {
        // test no separation
        val noSepBase = ":potato::potato::potato:"
        val noSepReplaceSpaces = ":potato: :potato: :potato:"
        val noSepTarget = ":potato:"
        val noSepReplace = ":taco:"

        assertEquals(":taco::taco::taco:", DiscordPier.replaceTarget(noSepBase, noSepTarget, noSepReplace, requireSeparation = false))
        assertEquals(":taco: :taco: :taco:", DiscordPier.replaceTarget(noSepReplaceSpaces, noSepTarget, noSepReplace, requireSeparation = false))

        // test require separation
        val noLeadingChars = "@Z750 some text"
        val middleOfStr = "some text @Z750 some more"
        val endOfStr = "some text @Z750"
        val failNoSep = "some text@Z750more text"
        val mixedCase = "@Z750 should replace but@Z750should not"

        val target = "@Z750"
        val replacement = "12345"

        assertEquals(noLeadingChars.replace(target, replacement), DiscordPier.replaceTarget(noLeadingChars, target, replacement))
        assertEquals(middleOfStr.replace(target, replacement), DiscordPier.replaceTarget(middleOfStr, target, replacement))
        assertEquals(endOfStr.replace(target, replacement), DiscordPier.replaceTarget(endOfStr, target, replacement))
        assertEquals(failNoSep, DiscordPier.replaceTarget(failNoSep, target, replacement))
        assertEquals("12345 should replace but@Z750should not", DiscordPier.replaceTarget(mixedCase, target, replacement))
    }

    @Test
    fun testUsernameValidation() {
        val minimumAcceptedLength = 2
        val maximumAcceptedLength = 32

        val tooShort = "1" // length = 1
        val tooLong = "123456789012345678901234567890123" // length = 33
        val okay = "SomeUsername"

        val edgeCaseLower = "12" // length = 2
        val edgeCaseHigher = "12345678901234567890123456789012" // length = 32

        assertEquals(edgeCaseLower, DiscordPier.enforceSenderName(edgeCaseLower))
        assertEquals(edgeCaseHigher, DiscordPier.enforceSenderName(edgeCaseHigher))
        assertEquals(okay, DiscordPier.enforceSenderName(okay))

        assertTrue(DiscordPier.enforceSenderName(tooShort).length >= minimumAcceptedLength)
        assertTrue(DiscordPier.enforceSenderName(tooLong).length <= maximumAcceptedLength)
    }
}
