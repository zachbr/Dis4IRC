/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2022 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

/**
 * Counts the number of occurrences of the substring in the base string
 */
fun countSubstring(baseStr: String, subStr: String): Int {
    if (baseStr.isEmpty()) {
        return 0
    }

    var count = 0
    var index = 0
    while (index != -1) {
        index = baseStr.indexOf(subStr, index)
        if (index != -1) {
            count++
            index += subStr.length
        }
    }

    return count
}
