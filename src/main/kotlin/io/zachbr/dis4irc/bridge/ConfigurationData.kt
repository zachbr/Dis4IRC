/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import ninja.leaping.configurate.commented.CommentedConfigurationNode
import java.util.regex.Pattern

val MATCH_ALL_INDIV = Regex(".*")
/**
 * Main bridge configuration data class
 */
data class BridgeConfiguration(
    val bridgeName: String,
    val ircHostname: String,
    val ircPassword: String?,
    val ircPort: Int,
    val ircSslEnabled: Boolean,
    val ircAllowInvalidCerts: Boolean,
    val ircNickName: String,
    val ircUserName: String,
    val ircRealName: String,
    val ircAntiPing: Boolean,
    val ircNoPrefixRegex: Pattern?,
    val ircCommandsInit: List<String>,
    val discordApiKey: String,
    val announceJoinsQuits: Boolean,
    val discordWebHooks: List<WebhookMapping>,
    val channelMappings: List<ChannelMapping>,
    val rawNode: CommentedConfigurationNode
) {
    /** toString with no sensitive info */
    fun toLoggable(): String {
        return "BridgeConfiguration(bridgeName='$bridgeName', " +
                "ircHostname='$ircHostname', " +
                "ircPassword=${ircPassword?.replace(MATCH_ALL_INDIV, "*")}, " +
                "ircPort=$ircPort, " +
                "ircSslEnabled=$ircSslEnabled, " +
                "ircAllowInvalidCerts=$ircAllowInvalidCerts, " +
                "ircNickName='$ircNickName', " +
                "ircUserName='$ircUserName', " +
                "ircRealName='$ircRealName', " +
                "ircAntiPing=$ircAntiPing, " +
                "ircNoPrefixRegex=$ircNoPrefixRegex, " +
                "ircCommandsInit=$ircCommandsInit, " +
                "discordApiKey='${discordApiKey.replace(MATCH_ALL_INDIV, "*")}', " +
                "announceJoinsQuits=$announceJoinsQuits, " +
                "discordWebHooks=${discordWebHooks.map { it.toLoggable() }}, " +
                "channelMappings=$channelMappings, " +
                "rawNode=(hash) ${rawNode.hashCode()})"
    }
}

/**
 * Simple channel-to-channel configuration data class
 */
data class ChannelMapping(
    val discordChannel: String,
    val ircChannel: String
)

/**
 * Simple discord-to-webhook configuration data class
 */
data class WebhookMapping(
    val discordChannel: String,
    val webhookUrl: String
) {
    /** toString with no sensitive info */
    fun toLoggable(): String {
        return "WebhookMapping(discordChannel='$discordChannel', webhookUrl='${webhookUrl.substring(0, 60)}')"
    }
}
