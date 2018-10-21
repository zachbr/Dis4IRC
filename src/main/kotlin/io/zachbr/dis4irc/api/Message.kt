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

package io.zachbr.dis4irc.api

class Message(
    /**
     * Message content, '\n' for newlines
     */
    var contents: String,
    /**
     * Sender of the message
     */
    var sender: Sender,
    /**
     * Channel the message originated from
     */
    val channel: Channel,
    /**
     * Original receive timestamp in nanoseconds
     */
    val timestamp: Long
) {
    internal var destination = Destination.OPPOSITE

    /**
     * Gets whether the message should be sent to IRC
     */
    fun shouldSendToIrc(): Boolean {
        return when (destination) {
            Destination.BOTH -> true
            Destination.IRC -> true
            Destination.ORIGIN -> channel.type == Channel.Type.IRC
            Destination.OPPOSITE -> channel.type != Channel.Type.IRC
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
            Destination.ORIGIN -> channel.type == Channel.Type.DISCORD
            Destination.OPPOSITE -> channel.type != Channel.Type.DISCORD
            Destination.DISCORD -> true
        }
    }
}
