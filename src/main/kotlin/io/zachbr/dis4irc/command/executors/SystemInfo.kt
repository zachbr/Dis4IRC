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

package io.zachbr.dis4irc.command.executors

import io.zachbr.dis4irc.Dis4IRC
import io.zachbr.dis4irc.command.api.Executor
import io.zachbr.dis4irc.command.api.Sender
import io.zachbr.dis4irc.command.api.SimpleCommand

class SystemInfo : Executor() {

    private fun isAuthorized(sender: Sender): Boolean {
        if (sender.ircNickServ != null && sender.ircNickServ == "Z750") {
            return true
        }

        if (sender.discordId != null && sender.discordId == 107387791683416064) {
            return true
        }

        return false
    }

    override fun onCommand(command: SimpleCommand) {
        if (!isAuthorized(command.sender)) {
            return
        }

        val totalAllocated = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val currentMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024)
        val javaVersion = System.getProperty("java.runtime.version")
        val osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + "(" + System.getProperty("os.arch") + ")"

        command.output = "Uptime: ${Dis4IRC.Static.uptime}\n" +
                "Memory: $currentMemory / $totalAllocated (MiB)\n" +
                "Java: $javaVersion\n" +
                "OS: $osInfo"
        command.submit()
    }
}
