/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2023 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.kitteh.irc.client.library.util.Cutter

const val ZERO_WIDTH_SPACE = 0x200B.toChar()

// Test for GH-64. Originally for our own implementation, now just verify the underlying IRC library fix.
class IrcEncodedCutterTest {
    private val cutter = Cutter.DefaultWordCutter()

    @Test
    fun testCutterFastPath() {
        val input = "The quick brown fox jumps over the lazy dog"
        val size = 999 // beyond
        val output = cutter.split(input, size)
        assertEquals(input, output[0])
    }

    @Test
    fun testCutterAccuracy() {
        val input = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Porta nibh venenatis cras sed felis eget velit aliquet sagittis. Ut sem nulla pharetra diam sit amet nisl suscipit. Tempor commodo ullamcorper a lacus vestibulum sed. Felis bibendum ut tristique et egestas quis ipsum suspendisse ultrices. Tristique senectus et netus et malesuada fames ac turpis. Augue ut lectus arcu bibendum. Eget lorem dolor sed viverra. Consequat semper viverra nam libero. Est ante in nibh mauris. Sed viverra ipsum nunc aliquet bibendum. Sed odio morbi quis commodo odio aenean sed adipiscing diam. Dignissim sodales ut eu sem integer vitae justo eget. Volutpat blandit aliquam etiam erat. Elementum eu facilisis sed odio morbi quis. Vel pharetra vel turpis nunc eget lorem. Amet venenatis urna cursus eget nunc scelerisque viverra mauris in."
        val size = 150
        val output = cutter.split(input, size)

        val expected0 = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Porta nibh venenatis cras"
        val expected1 = "sed felis eget velit aliquet sagittis. Ut sem nulla pharetra diam sit amet nisl suscipit. Tempor commodo ullamcorper a lacus vestibulum sed. Felis"
        val expected2 = "bibendum ut tristique et egestas quis ipsum suspendisse ultrices. Tristique senectus et netus et malesuada fames ac turpis. Augue ut lectus arcu"
        val expected3 = "bibendum. Eget lorem dolor sed viverra. Consequat semper viverra nam libero. Est ante in nibh mauris. Sed viverra ipsum nunc aliquet bibendum. Sed"
        val expected4 = "odio morbi quis commodo odio aenean sed adipiscing diam. Dignissim sodales ut eu sem integer vitae justo eget. Volutpat blandit aliquam etiam erat."
        val expected5 = "Elementum eu facilisis sed odio morbi quis. Vel pharetra vel turpis nunc eget lorem. Amet venenatis urna cursus eget nunc scelerisque viverra mauris"
        val expected6 = "in."

        assertEquals(expected0, output[0])
        assertEquals(expected1, output[1])
        assertEquals(expected2, output[2])
        assertEquals(expected3, output[3])
        assertEquals(expected4, output[4])
        assertEquals(expected5, output[5])
        assertEquals(expected6, output[6])
    }

    @Test
    fun testCutterNoSpaces() {
        val input = "IconfessthatIdonotentirelyapproveofthisConstitutionatpresent;but,sir,IamnotsureIshallneverapproveit,for,havinglivedlong,Ihaveexperiencedmanyinstancesofbeingobliged,bybetterinformationorfullerconsideration,tochangeopinionsevenonimportantsubjects,whichIoncethoughtright,butfoundtobeotherwise"
        val size = 100
        val output = cutter.split(input, size)

        val expected0 = "IconfessthatIdonotentirelyapproveofthisConstitutionatpresent;but,sir,IamnotsureIshallneverapproveit,"
        val expected1 = "for,havinglivedlong,Ihaveexperiencedmanyinstancesofbeingobliged,bybetterinformationorfullerconsidera"
        val expected2 = "tion,tochangeopinionsevenonimportantsubjects,whichIoncethoughtright,butfoundtobeotherwise"

        assertEquals(expected0, output[0])
        assertEquals(expected1, output[1])
        assertEquals(expected2, output[2])
    }

    @Test
    fun testCutterUnicode() {
        val input = "<Ja${ZERO_WIDTH_SPACE}me${ZERO_WIDTH_SPACE}s> Faust complained about having two souls in his breast, but I harbor a whole crowd of them and they quarrel. It is like being in a republic."
        val size = 30
        val output = cutter.split(input, size)

        val expected0 = "<Ja${ZERO_WIDTH_SPACE}me${ZERO_WIDTH_SPACE}s> Faust complained"
        val expected1 = "about having two souls in his"
        val expected2 = "breast, but I harbor a whole"
        val expected3 = "crowd of them and they"
        val expected4 = "quarrel. It is like being in a"
        val expected5 = "republic."

        assertEquals(expected0, output[0])
        assertEquals(expected1, output[1])
        assertEquals(expected2, output[2])
        assertEquals(expected3, output[3])
        assertEquals(expected4, output[4])
        assertEquals(expected5, output[5])
    }

    @Test
    fun testCutterUnicodeEmoji() {
        val input = "<James> Faust complained about having two souls in his breast \uD83D\uDE14\uD83D\uDC65, but I harbor a whole crowd of them \uD83D\uDE05\uD83D\uDC65 and they quarrel. It is like being in a republic. \uD83E\uDD14\uD83C\uDFDB\uFE0F\uD83C\uDFAD"
        val size = 45
        val output = cutter.split(input, size)

        val expected0 = "<James> Faust complained about having two"
        val expected1 = "souls in his breast \uD83D\uDE14\uD83D\uDC65, but I harbor a"
        val expected2 = "whole crowd of them \uD83D\uDE05\uD83D\uDC65 and they"
        val expected3 = "quarrel. It is like being in a republic."
        val expected4 = "\uD83E\uDD14\uD83C\uDFDB\uFE0F\uD83C\uDFAD"

        assertEquals(expected0, output[0])
        assertEquals(expected1, output[1])
        assertEquals(expected2, output[2])
        assertEquals(expected3, output[3])
        assertEquals(expected4, output[4])
    }
}
