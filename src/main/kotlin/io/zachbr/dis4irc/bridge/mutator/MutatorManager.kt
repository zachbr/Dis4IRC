/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.bridge.mutator.mutators.BlockHereEveryone
import io.zachbr.dis4irc.bridge.mutator.mutators.PasteLongMessages
import io.zachbr.dis4irc.bridge.mutator.mutators.StripAntiPingCharacters
import io.zachbr.dis4irc.bridge.mutator.mutators.TranslateFormatting
import org.spongepowered.configurate.CommentedConfigurationNode

class MutatorManager(bridge: Bridge, config: CommentedConfigurationNode) {
    private val mutators = HashMap<Class<out Mutator>, Mutator>()

    init {
        registerMutator(StripAntiPingCharacters())
        registerMutator(BlockHereEveryone())
        registerMutator(PasteLongMessages(bridge, config.node("paste-service")))
        registerMutator(TranslateFormatting())
    }

    private fun registerMutator(mutator: Mutator) {
        mutators[mutator.javaClass] = mutator
    }

    /**
     * Applies all registered mutators to the bridge message and any referenced messages.
     */
    internal fun applyMutators(bMessage: BridgeMessage): BridgeMessage? {
        val platformMessage = bMessage.message
        if (platformMessage is DiscordMessage && platformMessage.referencedMessage != null) {
            runMutatorLoop(platformMessage.referencedMessage, mutableSetOf()) // separate tracking
        }

        val finalLifeCycle = runMutatorLoop(platformMessage, bMessage.getAppliedMutators())
        return when (finalLifeCycle) {
            Mutator.LifeCycle.STOP_AND_DISCARD -> null
            else -> bMessage // Covers CONTINUE and RETURN_EARLY
        }
    }

    /**
     * Bypass function to intentionally call a mutator on a platform message.
     * (No tracking of previously applied)
     */
    internal fun applyMutator(clazz: Class<out Mutator>, message: PlatformMessage) {
        val mutator = mutators[clazz] ?: throw NoSuchElementException("No mutator with class type: ${clazz.simpleName}")
        mutator.mutate(message)
    }

    /**
     * Tuns the entire loop of mutators on a single message part.
     *
     * @param message The platform message to mutate.
     * @param appliedSet The set used to track which mutators have run for this task.
     * @return The final lifecycle state after the loop finishes or is interrupted.
     */
    private fun runMutatorLoop(message: PlatformMessage, appliedSet: MutableSet<Class<out Mutator>>): Mutator.LifeCycle {
        for (mutator in mutators.values) {
            val state = applySingleMutator(mutator, message, appliedSet)
            if (state != Mutator.LifeCycle.CONTINUE) {
                return state
            }
        }
        return Mutator.LifeCycle.CONTINUE
    }

    /**
     * Applies a single mutator to the given bridge message.
     */
    private fun applySingleMutator(mutator: Mutator, message: PlatformMessage, appliedSet: MutableSet<Class<out Mutator>>): Mutator.LifeCycle {
        if (appliedSet.contains(mutator.javaClass)) {
            return Mutator.LifeCycle.CONTINUE
        }

        val state = mutator.mutate(message)
        appliedSet.add(mutator.javaClass)
        return state
    }
}