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

package io.zachbr.dis4irc.bridge.pier

import io.zachbr.dis4irc.api.Message
import io.zachbr.dis4irc.bridge.BridgeConfiguration

interface Pier {

    /**
     * Initializes a pier, connecting it to whatever backend it needs, and readying it for use
     */
    fun init(config: BridgeConfiguration)

    /**
     * Safely shuts down a pier
     */
    fun shutdown()

    /**
     * Sends a message through this pier
     */
    fun sendMessage(targetChan: String, msg: Message)
}
