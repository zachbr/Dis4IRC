/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

import org.commonmark.Extension
import org.commonmark.internal.util.Parsing
import org.commonmark.node.Block
import org.commonmark.node.CustomBlock
import org.commonmark.parser.InlineParser
import org.commonmark.parser.Parser
import org.commonmark.parser.block.*
import java.lang.StringBuilder

const val FENCE_CHAR = '|'
const val FENCE_LENGTH = 2

// Supports discord flavored spoilers
// based on org.commonmark.internal.FencedCodeBlockParser

class DiscordSpoiler : CustomBlock()

class DiscordSpoilerExtension private constructor() : Parser.ParserExtension {

    companion object {
        @JvmStatic
        fun create(): Extension {
            return DiscordSpoilerExtension()
        }
    }

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customBlockParserFactory(DiscordSpoilerParser.Factory())
    }
}

class DiscordSpoilerParser : AbstractBlockParser() {
    private val block = DiscordSpoiler()
    private var lines = StringBuilder()

    override fun tryContinue(state: ParserState): BlockContinue? {
        var newIndex = state.index
        val line = state.line
        if (isClosing(line, state.nextNonSpaceIndex)) {
            return BlockContinue.finished()
        } else {
            while (newIndex < line.length && line[newIndex].isWhitespace()) {
                newIndex++
            }
        }
        return BlockContinue.atIndex(newIndex)
    }

    override fun addLine(line: CharSequence) {
        val l = replaceTarget(line.toString(), "||", "", requireSeparation = false)
        lines.append("$l\n")
    }

    override fun parseInlines(inlineParser: InlineParser) {
        inlineParser.parse(lines.toString(), block)
    }


    override fun getBlock(): Block? {
        return block
    }

    class Factory : AbstractBlockParserFactory() {

        override fun tryStart(state: ParserState, matchedBlockParser: MatchedBlockParser): BlockStart? {
            val nextNonSpace = state.nextNonSpaceIndex
            return if (checkOpener(state.line, nextNonSpace)) {
                BlockStart.of(DiscordSpoilerParser()).atIndex(nextNonSpace + FENCE_LENGTH)
            } else {
                BlockStart.none()
            }
        }
    }

    private fun isClosing(line: CharSequence, index: Int): Boolean {
        val fences = Parsing.skip(FENCE_CHAR, line, index, line.length) - index
        if (fences < FENCE_LENGTH) {
            return false
        }

        return true
    }
}

fun checkOpener(line: CharSequence, startingIndex: Int): Boolean {
    var matches = 0
    var i = startingIndex
    while (i < line.length) {
        if (line[i] == FENCE_CHAR) {
            matches++

            if (matches >= FENCE_LENGTH) {
                return true
            }
        }

        i++
    }

    return false
}
