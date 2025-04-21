/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

import org.commonmark.Extension
import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.parser.delimiter.DelimiterProcessor
import org.commonmark.parser.delimiter.DelimiterRun

private const val DELIMITER = '|'
private const val DELIMITER_LENGTH = 2

class DiscordSpoiler : CustomNode()

class DiscordSpoilerExtension private constructor() : Parser.ParserExtension {

    companion object {
        @JvmStatic fun create(): Extension = DiscordSpoilerExtension()
    }

    override fun extend(parserBuilder: Parser.Builder) {
        parserBuilder.customDelimiterProcessor(SpoilerDelimiterProcessor())
    }
}

private class SpoilerDelimiterProcessor : DelimiterProcessor {
    override fun getOpeningCharacter() = DELIMITER
    override fun getClosingCharacter() = DELIMITER
    override fun getMinLength() = DELIMITER_LENGTH

    override fun process(openingRun: DelimiterRun, closingRun: DelimiterRun): Int {
        val opener = openingRun.opener
        val closer = closingRun.closer
        val spoiler = DiscordSpoiler()

        var node: Node? = opener.next
        while (node != null && node !== closer) {
            val next = node.next
            spoiler.appendChild(node)
            node = next
        }

        opener.insertAfter(spoiler)
        return DELIMITER_LENGTH
    }
}
