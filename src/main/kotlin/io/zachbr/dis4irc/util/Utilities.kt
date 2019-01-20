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
