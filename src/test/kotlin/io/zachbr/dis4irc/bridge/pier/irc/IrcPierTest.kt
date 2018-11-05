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

package io.zachbr.dis4irc.bridge.pier.irc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IrcPierTest {
    @Test
    fun testAntiPing() {
        assertEquals("k\u200Bit\u200Bte\u200Bn", IrcPier.rebuildWithAntiPing("kitten"))
        assertEquals("k\u200Bit\u200Bte\u200Bn \u200B\uD83C\uDF57\u200B", IrcPier.rebuildWithAntiPing("kitten \uD83C\uDF57"))
    }
}