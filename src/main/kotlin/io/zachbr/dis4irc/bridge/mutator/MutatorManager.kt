package io.zachbr.dis4irc.bridge.mutator

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.bridge.mutator.mutators.BlockHereEveryone

class MutatorManager(bridge: Bridge) {
    private val mutators = ArrayList<Mutator>()

    init {
        registerMutator(BlockHereEveryone())
    }

    private fun registerMutator(mutator: Mutator) {
        mutators.add(mutator)
    }

    internal fun applyMutators(message: Message): String? {
        val iterator = mutators.iterator()
        var mutated: String? = message.contents
        while (iterator.hasNext()) {
            if (mutated == null) {
                return null
            }

            mutated = iterator.next().mutate(mutated, message.channel)
        }

        return mutated
    }
}
