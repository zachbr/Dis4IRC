/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2022 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

/**
 * Light wrapper around LongArrays to remove old entries as new ones are added
 */
class WrappingLongArray(private val size: Int) {
    private val backing = LongArray(size)
    private var index = 0
    private var hasWrappedAround = false

    /**
     * Adds an element to the underlying array
     *
     * If this array is fully populated, the oldest element is replaced with the newly added element
     */
    fun add(e: Long) {
        wrapIndex()
        backing[index] = e
        index += 1
    }

    /**
     * Returns a copy of the backing LongArray up to the latest element that has been populated
     *
     * This does NOT return a full size array unless it has been fully populated
     */
    fun toLongArray(): LongArray {
        return if (hasWrappedAround) {
            backing.copyOf()
        } else {
            backing.copyOf(index)
        }
    }

    private fun wrapIndex() {
        if (index == size) {
            index = 0
            hasWrappedAround = true
        }
    }
}
