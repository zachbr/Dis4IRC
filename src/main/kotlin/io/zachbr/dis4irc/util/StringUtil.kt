/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2024 Dis4IRC contributors
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

/**
 * Given a string, find the target and replace it, optionally requiring whitespace separation to replace
 */
fun replaceTarget(base: CharSequence, target: String, replacement: CharSequence, requireSeparation: Boolean = false): String {
    var out = base

    fun isWhiteSpace(i: Int): Boolean {
        return i == -1 || i == out.length || !requireSeparation || out[i].isWhitespace()
    }

    fun isTarget(i: Int): Boolean {
        return i > 0 && out[i - 1] == '<'
    }

    var start = out.indexOf(target, 0)
    while (start > -1) {
        val end = start + target.length
        val nextSearchStart = start + replacement.length

        if (isWhiteSpace(start - 1) && isWhiteSpace(end) && !isTarget(start)) {
            out = out.replaceRange(start, start + target.length, replacement)
        }

        start = out.indexOf(target, nextSearchStart)
    }

    return out.toString()
}

