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

data class Sender(
    /**
     * User's display name, this is *not* guaranteed to be unique or secure
     */
    val displayName: String,
    /**
     * User's discord snowflake id, or null if the message originated from IRC
     */
    val discordId: Long?,
    /**
     * User's nickserv account name, or null if the message originated from IRC or the IRC network doesn't support it
     */
    val ircNickServ: String?
)
