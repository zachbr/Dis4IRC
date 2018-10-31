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

package io.zachbr.dis4irc.config

import io.zachbr.dis4irc.Dis4IRC.Static.logger
import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.ChannelMapping
import io.zachbr.dis4irc.bridge.WebhookMapping
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.commented.CommentedConfigurationNode

/**
 * Creates a "default" node, pre-populated with settings
 *
 * @throws IllegalArgumentException if called from any node other than direct children of the bridges node
 */
fun CommentedConfigurationNode.makeDefaultNode() {
    if (this.parent?.key != "bridges") {
        throw IllegalArgumentException("Cannot make default node from anything but a direct child of bridges node!")
    }

    this.setComment(
        "A bridge is a single bridged connection operating in its own space away from all the other bridges\n" +
                "Most people will only need this one default bridge"
    )

    val ircBaseNode = this.getNode("irc")
    ircBaseNode.setComment("Configuration for connecting to the IRC server")

    val ircServerNode = ircBaseNode.getNode("server")
    ircServerNode.value = "127.0.0.1"

    val ircServerPass = ircBaseNode.getNode("password")
    ircServerPass.value = null

    val ircPortNode = ircBaseNode.getNode("port")
    ircPortNode.value = "6697"

    val ircSslEnabled = ircBaseNode.getNode("use-ssl")
    ircSslEnabled.value = true

    val ircNickName = ircBaseNode.getNode("nickname")
    ircNickName.value = "BridgeBot"

    val ircUserName = ircBaseNode.getNode("username")
    ircUserName.value = "BridgeBot"

    val ircRealName = ircBaseNode.getNode("realname")
    ircRealName.value = "BridgeBot"

    val ircAntiPing = ircBaseNode.getNode("anti-ping")
    ircAntiPing.value = true

    val noPrefixString = ircBaseNode.getNode("no-prefix-str")
    noPrefixString.value = null
    noPrefixString.setComment("Messages prefixed with this value will be passed to IRC without a user prefix")

    val discordApiKey = this.getNode("discord-api-key")
    discordApiKey.setComment("Your discord API key you registered your bot with")
    discordApiKey.value = ""

    val discordWebhookParent = this.getNode("discord-webhooks")
    discordWebhookParent.setComment("Match a channel id to a webhook URL to enable webhooks for that channel")

    val discordWebhook = discordWebhookParent.getNode("discord-channel-id")
    discordWebhook.value = "http://webhook-url"

    val mappingsNode = this.getNode("channel-mappings")
    mappingsNode.setComment("Mappings are the channel <-> channel bridging configurations")

    val discordChannelNode = mappingsNode.getNode("discord-channel-id")
    discordChannelNode.value = "irc-channel-name"
}

/**
 * Converts a given node into a BridgeConfiguration instance
 *
 * @throws IllegalArgumentException if called from any node other than direct children of the bridges node
 */
fun ConfigurationNode.toBridgeConfiguration(): BridgeConfiguration {
    if (this.parent?.key != "bridges") {
        throw IllegalArgumentException("Cannot make bridge configuration from anything but a direct child of bridges node!")
    }

    fun getStringNonNull(errMsg: String, vararg path: String): String {
        val node = this.getNode(*path)
        return node.string ?: throw IllegalArgumentException(errMsg)
    }

    val bridgeName = this.key as String

    val ircHost = getStringNonNull("IRC hostname cannot be null in $bridgeName", "irc", "server")
    val ircPass = this.getNode("irc", "password").string // nullable
    val ircPort = this.getNode("irc", "port").int
    val ircUseSsl = this.getNode("irc", "use-ssl").boolean
    val ircNickName = getStringNonNull("IRC nickname cannot be null in $bridgeName!", "irc", "nickname")
    val ircUserName = getStringNonNull("IRC username cannot be null in $bridgeName!", "irc", "username")
    val ircRealName = getStringNonNull("IRC realname cannot be null in $bridgeName!", "irc", "realname")
    val ircAntiPing = this.getNode("irc", "anti-ping").boolean
    val ircNoPrefix = this.getNode("irc", "no-prefix-str").string // nullable
    val discordApiKey = getStringNonNull("Discord API key cannot be null in $bridgeName!", "discord-api-key")

    val webhookMappings = ArrayList<WebhookMapping>()
    for (webhookNode in this.getNode("discord-webhooks").childrenMap.values) {
        val mapping = webhookNode.toWebhookMapping()
        if (mapping != null) {
            webhookMappings.add(mapping)
        }
    }

    val channelMappings = ArrayList<ChannelMapping>()
    for (mappingNode in this.getNode("channel-mappings").childrenMap.values) {
        channelMappings.add(mappingNode.toChannelMapping())
    }

    var validated = true
    fun validateStringNotEmpty(string: String, errMsg: String) {
        if (string.trim().isEmpty()) {
            logger.error("$errMsg for bridge: $bridgeName")
            validated = false
        }
    }

    validateStringNotEmpty(ircHost, "IRC hostname left empty")
    validateStringNotEmpty(ircNickName, "IRC nickname left empty")
    validateStringNotEmpty(ircUserName, "IRC username left empty")
    validateStringNotEmpty(ircRealName, "IRC realname left empty")
    validateStringNotEmpty(discordApiKey, "Discord API key left empty")

    if (ircPass != null) {
        validateStringNotEmpty(ircPass, "IRC pass cannot be left empty")
    }

    if (ircNoPrefix != null) {
        validateStringNotEmpty(ircNoPrefix, "IRC no prefix str cannot be left empty")
    }

    if (ircPort == 0) {
        logger.error("IRC server port invalid for bridge: $bridgeName")
        validated = false
    }

    if (channelMappings.size == 0) {
        logger.error("No channel mappings defined for bridge: $bridgeName")
        validated = false
    }

    if (!validated) {
        throw IllegalArgumentException("Cannot start $bridgeName bridge with above configuration errors!")
    }

    return BridgeConfiguration(
        bridgeName,
        ircHost,
        ircPass,
        ircPort,
        ircUseSsl,
        ircNickName,
        ircUserName,
        ircRealName,
        ircAntiPing,
        ircNoPrefix,
        discordApiKey,
        webhookMappings,
        channelMappings
    )
}

/**
 * Converts a given node into a channel mapping configuration instance
 */
fun ConfigurationNode.toChannelMapping(): ChannelMapping {
    val discordChannel: String = this.key as String
    val ircChannel: String = this.string ?: throw IllegalArgumentException("IRC channel mapping cannot be null")

    return ChannelMapping(discordChannel, ircChannel)
}

/**
 * Converts a given node into a webhook mapping configuration instance
 */
fun ConfigurationNode.toWebhookMapping(): WebhookMapping? {
    val discordChannel: String? = this.key as String
    val webhookUrl: String? = this.string

    if (discordChannel == null || webhookUrl == null) {
        return null
    }

    return WebhookMapping(discordChannel, webhookUrl)
}
