/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.util.countSubstring
import org.spongepowered.configurate.CommentedConfigurationNode
import kotlin.math.min

class ShortenLongMessages(val bridge: Bridge, config: CommentedConfigurationNode) : Mutator {
    private var maxNewlines: Int = 6
    private var maxMsgLength: Int = 1800
    private var shortenedSuffix: String = "[...]"

    init {
        val msgNewlineCount = config.node("max-new-lines")
        if (msgNewlineCount.virtual()) {
            msgNewlineCount.set(maxNewlines)
        }

        val msgLengthNode = config.node("max-message-length")
        if (msgLengthNode.virtual()) {
            msgLengthNode.set(maxMsgLength)
        }

        val shortenedSuffixNode = config.node("shortened-suffix")
        if (shortenedSuffixNode.virtual()) {
            shortenedSuffixNode.comment("Suffix to append to shortened messages")
            shortenedSuffixNode.set(shortenedSuffix)
        }

        maxNewlines = msgNewlineCount.int
        maxMsgLength = msgLengthNode.int
        shortenedSuffix = shortenedSuffixNode.string ?: shortenedSuffix
    }

    override fun mutate(message: PlatformMessage): Mutator.LifeCycle {
        if (message.source !is DiscordSource) {
            return Mutator.LifeCycle.CONTINUE
        }

        val contents = message.contents
        var shouldShorten = false
        if (countSubstring(contents, "\n") > maxNewlines) {
            shouldShorten = true
        }

        if (countSubstring(contents, "```") >= 2) {
            shouldShorten = true
        }

        if (contents.length > maxMsgLength) {
            shouldShorten = true
        }

        if (!shouldShorten) {
            return Mutator.LifeCycle.CONTINUE
        }

        // todo - there's almost certainly a better way to handle markdown as part of the formatter
        val cleaned = contents
            .replace("```", "")
            .replace("`", "")

        val maxLength = min(maxMsgLength, cleaned.length)
        val shortened = cleaned.substring(0, maxLength) + shortenedSuffix
        message.contents = "$shortened${IrcFormattingCodes.RESET}"

        return Mutator.LifeCycle.CONTINUE
    }
}
