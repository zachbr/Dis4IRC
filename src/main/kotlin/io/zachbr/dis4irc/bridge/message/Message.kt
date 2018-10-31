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
    val attachments: MutableList<String>?,
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
     * Gets whether the message should be sent to IRC
     */
    fun shouldSendToIrc(): Boolean {
        return when (destination) {
            Destination.BOTH -> true
            Destination.IRC -> true
            Destination.ORIGIN -> source.type == Source.Type.IRC
            Destination.OPPOSITE -> source.type != Source.Type.IRC
            Destination.DISCORD -> false
        }
    }

    /**
     * Gets whether the message should be sent to Discord
     */
    fun shouldSendToDiscord(): Boolean {
        return when (destination) {
            Destination.BOTH -> true
            Destination.IRC -> false
            Destination.ORIGIN -> source.type == Source.Type.DISCORD
            Destination.OPPOSITE -> source.type != Source.Type.DISCORD
            Destination.DISCORD -> true
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
