/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

/**
 * Counts the number of occurrences of the substring in the base string
 */
fun countSubstring(baseStr: String, subStr: String): Int = baseStr.split(subStr).size - 1

/**
 * Given a string, find the target and replace it, optionally requiring whitespace separation to replace
 */
fun replaceTarget(base: String, target: String, replacement: String, requireSeparation: Boolean = true): String {
    var out = base

    fun isWhiteSpace(i: Int): Boolean {
        return i == -1 || i == out.length || !requireSeparation || out[i].isWhitespace()
    }

    var start = out.indexOf(target, 0)
    while (start > -1) {
        val end = start + target.length
        val nextSearchStart = start + replacement.length

        if (isWhiteSpace(start - 1) && isWhiteSpace(end)) {
            out = out.replaceFirst(target, replacement)
        }

        start = out.indexOf(target, nextSearchStart)
    }

    return out
}
