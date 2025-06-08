/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.message

import io.zachbr.dis4irc.bridge.mutator.api.Mutator

data class BridgeMessage(
    val message: PlatformMessage,
    var destination: Destination = Destination.OPPOSITE,
    private val appliedMutators: MutableSet<Class<out Mutator>> = HashSet()
) {
    /**
     * Gets whether the message should be sent to the given platform.
     */
    fun shouldSendTo(platform: PlatformType): Boolean {
        return when (this.destination) {
            Destination.BOTH -> true
            Destination.IRC -> platform == PlatformType.IRC
            Destination.DISCORD -> platform == PlatformType.DISCORD
            Destination.ORIGIN -> {
                when (message) {
                    is DiscordMessage -> platform == PlatformType.DISCORD
                    is IrcMessage -> platform == PlatformType.IRC
                    is CommandMessage -> false // originate from bridge, if same, nowhere to bridge to // TODO: send to logger?
                }
            }
            Destination.OPPOSITE -> {
                when (message) {
                    is DiscordMessage -> platform == PlatformType.IRC
                    is IrcMessage -> platform == PlatformType.DISCORD
                    is CommandMessage -> false // originate from bridge, if opposite, what is opposite? // TODO: send to logger?
                }
            }
        }
    }

    fun originatesFromBridgeItself(): Boolean = message.sender == BridgeSender
    fun markMutatorApplied(clazz: Class<out Mutator>) = appliedMutators.add(clazz)
    fun hasAlreadyApplied(clazz: Class<out Mutator>): Boolean = appliedMutators.contains(clazz)
    internal fun getAppliedMutators(): MutableSet<Class<out Mutator>> = this.appliedMutators
}
