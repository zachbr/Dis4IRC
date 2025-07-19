/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.util.replaceTarget
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DiscordPierTest {
    @Test
    fun testReplaceTarget() {
        // test no separation
        val noSepBase = ":potato::potato::potato:"
        val noSepReplaceSpaces = ":potato: :potato: :potato:"
        val noSepTarget = ":potato:"
        val noSepReplace = ":taco:"

        assertEquals(":taco::taco::taco:", replaceTarget(noSepBase, noSepTarget, noSepReplace))
        assertEquals(":taco: :taco: :taco:", replaceTarget(noSepReplaceSpaces, noSepTarget, noSepReplace))

        // test require separation
        val noLeadingChars = "@Z750 some text"
        val middleOfStr = "some text @Z750 some more"
        val endOfStr = "some text @Z750"
        val failNoSep = "some text@Z750more text"
        val mixedCase = "@Z750 should replace but@Z750should not"

        val target = "@Z750"
        val replacement = "12345"

        assertEquals(noLeadingChars.replace(target, replacement), replaceTarget(noLeadingChars, target, replacement, requireSeparation = true))
        assertEquals(middleOfStr.replace(target, replacement), replaceTarget(middleOfStr, target, replacement, requireSeparation = true))
        assertEquals(endOfStr.replace(target, replacement), replaceTarget(endOfStr, target, replacement, requireSeparation = true))
        assertEquals(failNoSep, replaceTarget(failNoSep, target, replacement, requireSeparation = true))
        assertEquals("12345 should replace but@Z750should not", replaceTarget(mixedCase, target, replacement, requireSeparation = true))
    }

    @Test
    fun testReplaceRepeatingTarget() {
        // test emotes
        val singleEmote = "Have you seen :5950x:"
        val singleReplacement = "<:5950x:831359320453021696>"
        assertEquals("Have you seen <:5950x:831359320453021696>", replaceTarget(singleEmote, ":5950x:", singleReplacement))

        val multipleEmoteSep = "More is better :5950x: :5950x: :5950x:"
        assertEquals("More is better <:5950x:831359320453021696> <:5950x:831359320453021696> <:5950x:831359320453021696>", replaceTarget(multipleEmoteSep, ":5950x:", singleReplacement, true))

        val multipleEmoteNoSep = "More is better :5950x::5950x::5950x:"
        assertEquals("More is better <:5950x:831359320453021696><:5950x:831359320453021696><:5950x:831359320453021696>", replaceTarget(multipleEmoteNoSep, ":5950x:", singleReplacement, false))
        // test requireSeparation flag, below should not be replaced
        assertNotEquals("More is better <:5950x:831359320453021696><:5950x:831359320453021696><:5950x:831359320453021696>", replaceTarget(multipleEmoteNoSep, ":5950x:", singleReplacement, true))
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

        assertEquals(edgeCaseLower, enforceSenderName(edgeCaseLower))
        assertEquals(edgeCaseHigher, enforceSenderName(edgeCaseHigher))
        assertEquals(okay, enforceSenderName(okay))

        assertTrue(enforceSenderName(tooShort).length >= minimumAcceptedLength)
        assertTrue(enforceSenderName(tooLong).length <= maximumAcceptedLength)
    }

    @Test
    fun testUrlSuppressionWithBrackets() {
        val bare = "https://google.com"
        assertEquals("<https://google.com>", wrapUrlsInBrackets(bare))

        val leading = "$bare and some text"
        assertEquals("<https://google.com> and some text", wrapUrlsInBrackets(leading))

        val trailing = "Some text and $bare"
        assertEquals("Some text and <https://google.com>", wrapUrlsInBrackets(trailing))

        val ignored = "This case should be unchanged: don't touch."
        assertEquals(ignored, wrapUrlsInBrackets(ignored))

        val withParam = "https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8"
        assertEquals("<https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8>", wrapUrlsInBrackets(withParam))

        val withParamLeading = "$withParam seems like a good idea"
        assertEquals("<https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8> seems like a good idea", wrapUrlsInBrackets(withParamLeading))

        val withParamTrailing = "Have you seen $withParam"
        assertEquals("Have you seen <https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8>", wrapUrlsInBrackets(withParamTrailing))

        val withParamTrailingPunctuation = "$withParamTrailing??"
        assertEquals("Have you seen <https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8>??", wrapUrlsInBrackets(withParamTrailingPunctuation))

        val mixedNoSpace = "What is up withhttps://google.com" // yes the discord client handles this, no I am not sure why
        assertEquals("What is up with<https://google.com>", wrapUrlsInBrackets(mixedNoSpace))

        val mixedNoSpaceWithPuncutation = "$mixedNoSpace?"
        assertEquals("What is up with<https://google.com>?", wrapUrlsInBrackets(mixedNoSpaceWithPuncutation))

        val mixedNoSpaceWithMorePuncutation = "$mixedNoSpace?!?!?!?!?"
        assertEquals("What is up with<https://google.com>?!?!?!?!?", wrapUrlsInBrackets(mixedNoSpaceWithMorePuncutation))

        val multiple = "Has anyone found $withParam nearby? Anyone at all? I tried using $bare but it's not working!"
        assertEquals("Has anyone found <https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8> nearby? Anyone at all? I tried using <https://google.com> but it's not working!", wrapUrlsInBrackets(multiple))

        val alreadyWrappedIgnore = "Has anyone found <https://www.google.com/search?q=tacos+near+me&oq=tacos+near+me&sourceid=chrome&ie=UTF-8> nearby? Anyone at all? I tried using <https://google.com> but it's not working!"
        assertEquals(alreadyWrappedIgnore, wrapUrlsInBrackets(alreadyWrappedIgnore))
    }
}
