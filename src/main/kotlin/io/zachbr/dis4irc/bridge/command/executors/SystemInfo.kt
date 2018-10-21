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

package io.zachbr.dis4irc.bridge.command.executors

import io.zachbr.dis4irc.bridge.command.api.Executor
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Sender
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

class SystemInfo : Executor {

    /**
     * Checks that the sender is authorized to use this command
     */
    private fun isAuthorized(sender: Sender): Boolean {
        if (sender.ircNickServ != null && sender.ircNickServ == "Z750") {
            return true
        }

        if (sender.discordId != null && sender.discordId == 107387791683416064) {
            return true
        }

        return false
    }

    override fun onCommand(command: Message): String? {
        if (!isAuthorized(command.sender)) {
            return null
        }

        val runtimeMX = ManagementFactory.getRuntimeMXBean()

        val uptime = TimeUnit.MILLISECONDS.toDays(runtimeMX.uptime)
        val totalAllocated = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val currentMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024)
        val javaVersion = "${runtimeMX.vmName} ${runtimeMX.vmVersion}"
        val osInfo = System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + "(" + System.getProperty("os.arch") + ")"

        return "Uptime: $uptime days\n" +
                "Memory: $currentMemory / $totalAllocated (MiB)\n" +
                "Java: $javaVersion\n" +
                "OS: $osInfo"
    }
}
