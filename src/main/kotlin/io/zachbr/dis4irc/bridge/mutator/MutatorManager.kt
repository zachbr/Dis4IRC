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

import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.bridge.mutator.mutators.BlockHereEveryone
import io.zachbr.dis4irc.bridge.mutator.mutators.PasteLongMessages
import io.zachbr.dis4irc.bridge.mutator.mutators.TranslateFormatting

class MutatorManager {
    private val mutators = ArrayList<Mutator>()

    init {
        registerMutator(BlockHereEveryone())
        registerMutator(PasteLongMessages())
        registerMutator(TranslateFormatting())
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

            mutated = iterator.next().mutate(mutated, message.channel, message.sender, message.attachments)
        }

        return mutated
    }
}
