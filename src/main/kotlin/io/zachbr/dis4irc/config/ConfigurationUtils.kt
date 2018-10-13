package io.zachbr.dis4irc.config

import io.zachbr.dis4irc.Dis4IRC.Static.logger
import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.ChannelMapping
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.commented.CommentedConfigurationNode

fun CommentedConfigurationNode.makeDefaultNode() {
    this.setComment(
        "A bridge is a single bridged connection operating in its own space away from all the other bridges\n" +
                "Most people will only need this one default bridge"
    )

    val ircBaseNode = this.getNode("irc")
    ircBaseNode.setComment("Configuration for connecting to the IRC server")

    val ircServerNode = ircBaseNode.getNode("server")
    ircServerNode.value = "127.0.0.1"

    val ircPortNode = ircBaseNode.getNode("port")
    ircPortNode.value = "6697"

    val ircSslEnabled = ircBaseNode.getNode("use-ssl")
    ircSslEnabled.value = true

    val discordApiKey = this.getNode("discord-api-key")
    discordApiKey.setComment("Your discord API key you registered your bot with")
    discordApiKey.value = ""

    val mappingsNode = this.getNode("channel-mappings")
    mappingsNode.setComment("Mappings are the channel <-> channel bridging configurations")

    val discordChannelNode = mappingsNode.getNode("discord-channel-id")
    discordChannelNode.value = "irc-channel-name"
}

fun ConfigurationNode.toBridgeConfiguration(): BridgeConfiguration {
    val bridgeName = this.key as String
    val ircHost = this.getNode("irc", "server").string ?: throw IllegalArgumentException("IRC Hostname cannot be null!")
    val ircPort = this.getNode("irc", "port").int
    val ircUseSsl = this.getNode("irc", "ssl").boolean
    val discordApiKey = this.getNode("discord-api-key").string ?: throw IllegalArgumentException("Discord API key cannot be null!")

    val channelMappings = ArrayList<ChannelMapping>()
    for (mappingNode in this.getNode("channel-mappings").childrenList) {
        channelMappings.add(mappingNode.toChannelMapping())
    }

    var validated = true
    if (ircHost.trim().isEmpty()) {
        logger.warn("IRC server host left empty for bridge: $bridgeName")
        validated = false
    }

    if (ircPort == 0) {
        logger.warn("IRC server port invalid for bridge: $bridgeName")
        validated = false
    }

    if (discordApiKey.trim().isEmpty()) {
        logger.warn("Discord API key left empty for bridge: $bridgeName")
        validated = false
    }

    if (channelMappings.size == 0) {
        logger.warn("No channel mappings defined for bridge: $bridgeName")
        validated = false
    }

    if (!validated) {
        throw IllegalArgumentException("Cannot start $bridgeName bridge with above configuration errors!")
    }

    return BridgeConfiguration(bridgeName, ircHost, ircPort, ircUseSsl, discordApiKey, channelMappings)
}

fun ConfigurationNode.toChannelMapping(): ChannelMapping {
    val discordChannel: String = this.key as String
    val ircChannel: String = this.string ?: throw IllegalArgumentException("IRC channel mapping cannot be null")

    return ChannelMapping(discordChannel, ircChannel)
}
