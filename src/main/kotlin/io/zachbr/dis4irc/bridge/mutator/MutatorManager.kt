/*
 * This file is part of Dis4IRC.
 *
 * Dis4IRC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Dis4IRC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Dis4IRC.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.zachbr.dis4irc.bridge.mutator

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.bridge.mutator.mutators.BlockHereEveryone
import io.zachbr.dis4irc.bridge.mutator.mutators.PasteLongMessages
import io.zachbr.dis4irc.bridge.mutator.mutators.TranslateFormatting
import ninja.leaping.configurate.commented.CommentedConfigurationNode

class MutatorManager(bridge: Bridge, config: CommentedConfigurationNode) {
    private val mutators = ArrayList<Mutator>()

    init {
        registerMutator(BlockHereEveryone())
        registerMutator(PasteLongMessages(bridge, config.getNode("paste-service")))
        registerMutator(TranslateFormatting())
    }

    private fun registerMutator(mutator: Mutator) {
        mutators.add(mutator)
    }

    internal fun applyMutators(message: Message): Message? {
        val iterator = mutators.iterator()

        loop@ while (iterator.hasNext()) {
            val mutator = iterator.next()
            if (message.hasAlreadyApplied(mutator.javaClass)) {
                continue
            }

            val state = mutator.mutate(message)
            message.markMutatorApplied(mutator.javaClass)

            return when (state) {
                Mutator.LifeCycle.CONTINUE -> continue@loop
                Mutator.LifeCycle.STOP_AND_DISCARD -> null
                Mutator.LifeCycle.RETURN_EARLY -> message
            }
        }

        return message
    }
}
