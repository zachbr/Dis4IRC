/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.config

import io.zachbr.dis4irc.bridge.*
import io.zachbr.dis4irc.logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.ConfigurationNode
import java.util.regex.Pattern

/**
 * Creates a "default" node, pre-populated with settings
 *
 * @throws IllegalArgumentException if called from any node other than direct children of the bridges node
 */
fun CommentedConfigurationNode.makeDefaultNode() {
    if (this.parent()?.key() != "bridges") {
        throw IllegalArgumentException("Cannot make default node from anything but a direct child of bridges node!")
    }

    this.comment(
        "A bridge is a single bridged connection operating in its own space away from all the other bridges\n" +
                "Most people will only need this one default bridge"
    )

    val ircBaseNode = this.node("irc")
    ircBaseNode.comment("Configuration for connecting to the IRC server")

    val ircServerNode = ircBaseNode.node("server")
    ircServerNode.set("127.0.0.1")

    val ircServerPass = ircBaseNode.node("password")
    ircServerPass.set(null)

    val ircPortNode = ircBaseNode.node("port")
    ircPortNode.set("6697")

    val ircSslEnabled = ircBaseNode.node("use-ssl")
    ircSslEnabled.set(true)

    val ircAllowInvalidCerts = ircBaseNode.node("allow-invalid-ssl-certs")
    ircAllowInvalidCerts.set(false)

    val ircNickName = ircBaseNode.node("nickname")
    ircNickName.set("BridgeBot")

    val ircUserName = ircBaseNode.node("username")
    ircUserName.set("BridgeBot")

    val ircRealName = ircBaseNode.node("realname")
    ircRealName.set("BridgeBot")

    val ircAntiPing = ircBaseNode.node("anti-ping")
    ircAntiPing.set(true)

    val ircDiscriminator = ircBaseNode.node("discriminator")
    ircDiscriminator.set(true)
    ircDiscriminator.comment("Include the discriminator or the full tag if a nickname is set")

    val announceForwards = ircBaseNode.node("announce-forwarded-messages-sender")
    announceForwards.set(false)

    val noPrefixString = ircBaseNode.node("no-prefix-regex")
    noPrefixString.set(null)
    noPrefixString.comment("Messages that match this regular expression will be passed to IRC without a user prefix")

    val discordReplyContextLimit = ircBaseNode.node("discord-reply-context-limit")
    discordReplyContextLimit.set(90)
    discordReplyContextLimit.comment("Sets the max context length to use for messages that are Discord replies. 0 to disable.")

    val ircCommandsList = ircBaseNode.node("init-commands-list")
    ircCommandsList.set(arrayListOf("PRIVMSG NICKSERV info", "PRIVMSG NICKSERV help"))
    ircCommandsList.comment("A list of __raw__ irc messages to send")

    val discordApiKey = this.node("discord-api-key")
    discordApiKey.comment("Your discord API key you registered your bot with")
    discordApiKey.set("")

    val discordWebhookParent = this.node("discord-webhooks")
    discordWebhookParent.comment("Match a channel id to a webhook URL to enable webhooks for that channel")

    val discordWebhook = discordWebhookParent.node("discord-channel-id")
    discordWebhook.set("https://webhook-url")

    val announceJoinsQuits = this.node("announce-joins-and-quits")
    announceJoinsQuits.set(false)

    val announceExtras = this.node("announce-extras")
    announceExtras.set(false)

    val mappingsNode = this.node("channel-mappings")
    mappingsNode.comment("Mappings are the channel <-> channel bridging configurations")

    val discordChannelNode = mappingsNode.node("discord-channel-id")
    discordChannelNode.set("irc-channel-name")
}

/**
 * Converts a given node into a BridgeConfiguration instance
 *
 * @throws IllegalArgumentException if called from any node other than direct children of the bridges node
 */
fun CommentedConfigurationNode.toBridgeConfiguration(): BridgeConfiguration {
    if (this.parent()?.key() != "bridges") {
        throw IllegalArgumentException("Cannot make bridge configuration from anything but a direct child of bridges node!")
    }

    fun getStringNonNull(errMsg: String, vararg path: String): String {
        val node = this.node(*path)
        return node.string ?: throw IllegalArgumentException(errMsg)
    }

    val bridgeName = this.key() as String

    val ircHost = getStringNonNull("IRC hostname cannot be null in $bridgeName", "irc", "server")
    val ircPass = this.node("irc", "password").string // nullable
    val ircPort = this.node("irc", "port").int
    val ircUseSsl = this.node("irc", "use-ssl").boolean
    val ircAllowBadSsl = this.node("irc", "allow-invalid-ssl-certs").boolean
    val ircNickName = getStringNonNull("IRC nickname cannot be null in $bridgeName!", "irc", "nickname")
    val ircUserName = getStringNonNull("IRC username cannot be null in $bridgeName!", "irc", "username")
    val ircRealName = getStringNonNull("IRC realname cannot be null in $bridgeName!", "irc", "realname")
    val ircAntiPing = this.node("irc", "anti-ping").boolean
    val ircDiscriminator = this.node("irc", "discriminator").boolean
    val ircNoPrefix = this.node("irc", "no-prefix-regex").string // nullable
    val ircAnnounceForwards = this.node("irc", "announce-forwarded-messages-sender").boolean
    val ircDiscordReplyContextLimit = this.node("irc", "discord-reply-context-limit").int
    val ircCommandsChildren = this.node("irc", "init-commands-list").childrenList()
    val discordApiKey = getStringNonNull("Discord API key cannot be null in $bridgeName!", "discord-api-key")
    val announceJoinsQuits = this.node("announce-joins-and-quits").boolean
    val announceExtras = this.node("announce-extras").boolean

    val webhookMappings = ArrayList<WebhookMapping>()
    for (webhookNode in this.node("discord-webhooks").childrenMap().values) {
        val mapping = webhookNode.toWebhookMapping()
        if (mapping != null) {
            webhookMappings.add(mapping)
        }
    }

    val ircCommandsList = ArrayList<String>()
    for (node in ircCommandsChildren) {
        val command = node.string ?: continue
        ircCommandsList.add(command)
    }

    val channelMappings = ArrayList<ChannelMapping>()
    for (mappingNode in this.node("channel-mappings").childrenMap().values) {
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

    var ircNoPrefixPattern: Pattern? = null
    if (ircNoPrefix != null) {
        validateStringNotEmpty(ircNoPrefix, "IRC no prefix cannot be left empty")
        ircNoPrefixPattern = Pattern.compile(ircNoPrefix)
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

    val discordConfig = DiscordConfiguration(discordApiKey, webhookMappings)
    val ircConfig = IrcConfiguration(ircHost, ircPass, ircPort, ircUseSsl, ircAllowBadSsl, ircNickName, ircUserName,
        ircRealName, ircAntiPing, ircDiscriminator, ircNoPrefixPattern, ircAnnounceForwards, ircDiscordReplyContextLimit, ircCommandsList)

    return BridgeConfiguration(
        bridgeName,
        announceJoinsQuits,
        announceExtras,
        channelMappings,
        ircConfig,
        discordConfig,
        this
    )
}

/**
 * Converts a given node into a channel mapping configuration instance
 */
fun ConfigurationNode.toChannelMapping(): ChannelMapping {
    val discordChannel: String = this.key() as String
    val ircChannel: String = this.string ?: throw IllegalArgumentException("IRC channel mapping cannot be null")

    return ChannelMapping(discordChannel, ircChannel)
}

/**
 * Converts a given node into a webhook mapping configuration instance
 */
fun ConfigurationNode.toWebhookMapping(): WebhookMapping? {
    val discordChannel: String = this.key() as String
    val webhookUrl: String = this.string ?: return null

    return WebhookMapping(discordChannel, webhookUrl)
}
