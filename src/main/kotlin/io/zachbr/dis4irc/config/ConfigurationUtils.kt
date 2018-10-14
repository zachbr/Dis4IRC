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

    val ircServerPass = ircBaseNode.getNode("password")
    ircServerPass.value = null

    val ircPortNode = ircBaseNode.getNode("port")
    ircPortNode.value = "6697"

    val ircSslEnabled = ircBaseNode.getNode("use-ssl")
    ircSslEnabled.value = true

    val ircAcceptInvalidSsl = ircBaseNode.getNode("accept-invalid-certs")
    ircAcceptInvalidSsl.value = false

    val ircNickName = ircBaseNode.getNode("nickname")
    ircNickName.value = "BridgeBot"

    val ircUserName = ircBaseNode.getNode("username")
    ircUserName.value = "BridgeBot"

    val ircRealName = ircBaseNode.getNode("realname")
    ircRealName.value = "BridgeBot"

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

    val ircHost = this.getNode("irc", "server").string ?: throw IllegalArgumentException("IRC hostname cannot be null!")
    val ircPass = this.getNode("irc", "password").string // nullable
    val ircPort = this.getNode("irc", "port").int
    val ircUseSsl = this.getNode("irc", "use-ssl").boolean
    val ircInvalidSsl = this.getNode("irc, accept-invalid-certs").boolean
    val ircNickName = this.getNode("irc", "nickname").string ?: throw java.lang.IllegalArgumentException("IRC nickname cannot be null!")
    val ircUserName = this.getNode("irc", "username").getString("BridgeBot")
    val ircRealName = this.getNode("irc", "realname").getString("BridgeBot")
    val discordApiKey = this.getNode("discord-api-key").string ?: throw IllegalArgumentException("Discord API key cannot be null!")

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
        ircInvalidSsl,
        ircNickName,
        ircUserName,
        ircRealName,
        discordApiKey,
        channelMappings
    )
}

fun ConfigurationNode.toChannelMapping(): ChannelMapping {
    val discordChannel: String = this.key as String
    val ircChannel: String = this.string ?: throw IllegalArgumentException("IRC channel mapping cannot be null")

    return ChannelMapping(discordChannel, ircChannel)
}
