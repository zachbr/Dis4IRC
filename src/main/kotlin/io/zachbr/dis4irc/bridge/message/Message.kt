/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2023 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

import io.zachbr.dis4irc.bridge.mutator.api.Mutator

data class Message(
    /**
     * Message content, '\n' for newlines
     */
    var contents: String,
    /**
     * Sender of the message
     */
    var sender: Sender,
    /**
     * Source the message originated from
     */
    val source: Source,
    /**
     * Original receive timestamp in nanoseconds
     */
    val timestamp: Long,
    /**
     * A list of attachment URLs on the message
     */
    val attachments: MutableList<String> = ArrayList(),
    /**
     * The message that this message references. Will be null in most cases, only applicable to messages from Discord.
     * This is _not_ expected to resolve down infinitely (ie, reference that references another reference that ...)
     */
    val referencedMessage: Message? = null,
    /**
     * Embeds associated with the message. Only applicable to messages from Discord.
     */
    val embeds: List<Embed> = ArrayList(),
    /**
     * Destination to be bridged to
     */
    var destination: Destination = Destination.OPPOSITE,
    /**
     * A list of mutators that have been applied to this message
     * stored as class hashcodes because... reasons?
     */
    private val appliedMutators: MutableList<Int> = ArrayList()
) {
    /**
     * Gets whether the message should be sent to [destinationType]
     */
    fun shouldSendTo(destinationType: PlatformType): Boolean {
        return when (this.destination) {
            Destination.BOTH -> true
            Destination.IRC -> destinationType == PlatformType.IRC
            Destination.ORIGIN -> source.type == destinationType
            Destination.OPPOSITE -> source.type != destinationType
            Destination.DISCORD -> destinationType == PlatformType.DISCORD
        }
    }

    /**
     * Marks this message as having already been affected by a mutator
     */
    fun <T: Mutator> markMutatorApplied(clazz: Class<T>) = appliedMutators.add(clazz.hashCode())

    /**
     * Gets whether this class has been affected by the given mutator
     */
    fun <T: Mutator> hasAlreadyApplied(clazz: Class<T>): Boolean = appliedMutators.contains(clazz.hashCode())

    /**
     * Gets whether this message is a command message
     */
    fun originatesFromBridgeItself() = sender == BOT_SENDER
}

data class Embed(
    var string: String?,
    val imageUrl: String?
)
