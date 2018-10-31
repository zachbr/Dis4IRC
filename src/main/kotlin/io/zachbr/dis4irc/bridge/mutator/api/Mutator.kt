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

package io.zachbr.dis4irc.bridge.mutator.api

import io.zachbr.dis4irc.bridge.message.Message

/**
 * A mutator takes the given message contents and alters it in some way before returning it
 */
interface Mutator {
    /**
     * Called on a given message to mutate the contents
     *
     * @return the mutated message contents value or null if the message is not to be sent
     */
    fun mutate(message: Message): LifeCycle

    /**
     * Mutator Life Cycle control types
     */
    enum class LifeCycle {
        /**
         * Continue the lifecycle by passing this message onto the next
         */
        CONTINUE,
        /**
         * Stop the lifecycle and discard the message entirely
         */
        STOP_AND_DISCARD,
        /**
         * Stop the lifecycle and return the message as it exists currently
         */
        RETURN_EARLY
    }
}
