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

package io.zachbr.dis4irc.bridge

import net.dv8tion.jda.core.entities.MessageChannel
import org.kitteh.irc.client.library.element.Channel

/**
 * Responsible for maintaining the channel-to-channel mappings between IRC and Discord
 */
class ChannelMappingManager(conf: BridgeConfiguration) {
    private val discord2Irc = HashMap<String, String>()
    private val irc2Discord: Map<String, String>

    init {
        for (mapping in conf.channelMappings) {
            discord2Irc[mapping.discordChannel] = mapping.ircChannel
        }

        // reverse
        irc2Discord = discord2Irc.entries.associateBy({ it.value }) { it.key }
    }

    /**
     * Gets the IRC channel name to bridge to based on the Discord channel identifier
     */
    internal fun getMappingFor(discordChannel: MessageChannel): String? {
        var mapping = discord2Irc[discordChannel.id]

        if (mapping == null) {
            mapping = discord2Irc[discordChannel.name]
        }

        return mapping
    }

    /**
     * Gets the discord channel identifier to bridge to based on the IRC channel name
     */
    internal fun getMappingFor(ircChannel: Channel): String? {
        return irc2Discord[ircChannel.name]
    }
}
