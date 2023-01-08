/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2023 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.util

import io.zachbr.dis4irc.util.WrappingLongArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WrappingLongArrayTest {
    @Test
    fun testUnderPopulated() {
        val size = 50
        val population = 27
        val evicting = WrappingLongArray(size)

        // populate
        for (i in 0 until population) {
            evicting.add(i.toLong())
        }

        val arrayOut = evicting.toLongArray()
        // assert size - we care about population
        assertEquals(population, arrayOut.size)

        // assert contents correct
        for (i in 0 until population) {
            assertEquals(i.toLong(), arrayOut[i])
        }
    }

    @Test
    fun testFullyPopulated() {
        val size = 50
        val evicting = WrappingLongArray(size)

        // populate
        for (i in 0 until size) {
            evicting.add(i.toLong())
        }

        val arrayOut = evicting.toLongArray()
        // assert size correct
        assertEquals(size, arrayOut.size)

        // assert contents correct
        for (i in 0 until size) {
            assertEquals(i.toLong(), arrayOut[i])
        }
    }

    @Test
    fun testOverPopulated() {
        val size = 50
        val modifier = 5
        val evicting = WrappingLongArray(size)

        // populate
        for (i in 0 until size * modifier) {
            evicting.add(i.toLong())
        }

        val arrayOut = evicting.toLongArray()
        // assert size correct
        assertEquals(size, arrayOut.size)

        // assert contents correct
        for (i in 0 until size) {
            val expected = size * (modifier - 1) + i
            assertEquals(expected.toLong(), arrayOut[i])
        }
    }

    @Test
    fun testOverPopulatedBy1() {
        val size = 50
        val evicting = WrappingLongArray(size)

        // populate
        for (i in 0 until size + 1) { // over size
            evicting.add(i.toLong())
        }

        val arrayOut = evicting.toLongArray()
        // assert size correct
        assertEquals(size, arrayOut.size)
        // assert correct overflow
        assertEquals(50, arrayOut[0])
        assertEquals(1, arrayOut[1])
    }

    @Test
    fun testUnderPopulatedBy1() {
        val size = 50
        val evicting = WrappingLongArray(size)

        // populate
        for (i in 0 until size - 1) { // under size
            evicting.add(i.toLong())
        }

        val arrayOut = evicting.toLongArray()
        // assert size correct
        assertEquals(size - 1, arrayOut.size)
        // assert no overflow
        assertEquals(0, arrayOut[0])
    }
}
