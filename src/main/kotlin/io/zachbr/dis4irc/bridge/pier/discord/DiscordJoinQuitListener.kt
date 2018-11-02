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

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.hooks.ListenerAdapter

class DiscordJoinQuitListener(private val pier: DiscordPier) : ListenerAdapter() {
    private val logger = pier.logger

    override fun onGuildMemberJoin(event: GuildMemberJoinEvent?) {
        if (event == null) {
            logger.debug("Null Discord join event from JDA")
            return
        }

        val channel = event.guild.systemChannel
        val source = channel.asBridgeSource()

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD JOIN ${event.user.name}")

        val sender = BOT_SENDER
        val message = Message("${event.user.name} has joined the Discord", sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }

    override fun onGuildMemberLeave(event: GuildMemberLeaveEvent?) {
        if (event == null) {
            logger.debug("Null Discord leave event from JDA")
            return
        }

        val channel = event.guild.systemChannel
        val source = channel.asBridgeSource()

        // don't bridge itself
        if (pier.isThisBot(source, event.user.idLong)) {
            return
        }

        val receiveTimestamp = System.nanoTime()
        logger.debug("DISCORD PART ${event.user.name}")

        val sender = BOT_SENDER
        val message = Message("${event.user.name} has left the Discord", sender, source, receiveTimestamp)
        pier.sendToBridge(message)
    }
}
