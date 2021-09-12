/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import org.spongepowered.configurate.CommentedConfigurationNode
import java.util.regex.Pattern

val MATCH_ALL_INDIV = Regex(".")
/**
 * Main bridge configuration data class
 */
data class BridgeConfiguration(
    val bridgeName: String,
    val announceJoinsQuits: Boolean,
    val announceExtras: Boolean,
    val channelMappings: List<ChannelMapping>,
    val irc: IrcConfiguration,
    val discord: DiscordConfiguration,
    val rawNode: CommentedConfigurationNode
) {
    /** toString with no sensitive info */
    fun toLoggable(): String {
        return "BridgeConfiguration(" +
                "bridgeName='$bridgeName', " +
                "announceJoinsQuits=$announceJoinsQuits, " +
                "announceExtras=$announceExtras, " +
                "channelMappings=$channelMappings, " +
                irc.toLoggable() + " " +
                discord.toLoggable() + " " +
                "rawNode=(hash) ${rawNode.hashCode()})"
    }
}

data class IrcConfiguration (
    val hostname: String,
    val password: String?,
    val port: Int,
    val sslEnabled: Boolean,
    val allowInvalidCerts: Boolean,
    val nickName: String,
    val userName: String,
    val realName: String,
    val antiPing: Boolean,
    val discriminator: Boolean,
    val noPrefixRegex: Pattern?,
    val announceForwardedCommands: Boolean,
    val discordReplyContextLimit: Int,
    val startupRawCommands: List<String>
) {
    fun toLoggable(): String {
        return "IrcConfiguration(" +
                "hostname='$hostname', " +
                "password=${password?.replace(MATCH_ALL_INDIV, "*")}, " +
                "port=$port, " +
                "sslEnabled=$sslEnabled, " +
                "allowInvalidCerts=$allowInvalidCerts, " +
                "nickName='$nickName', " +
                "userName='$userName', " +
                "realName='$realName', " +
                "antiPing=$antiPing, " +
                "includeDiscriminator=$discriminator, " +
                "noPrefixRegex=$noPrefixRegex, " +
                "announceForwardedCommands=$announceForwardedCommands, " +
                "discordReplyContextLimit=$discordReplyContextLimit, " +
                "startupRawCommands=$startupRawCommands" +
                ")"
    }
}

data class DiscordConfiguration(
    val apiKey: String,
    val webHooks: List<WebhookMapping>
) {
    fun toLoggable(): String {
        return "DiscordConfiguration(" +
                "apiKey='${apiKey.replace(MATCH_ALL_INDIV, "*")}', " +
                "webHooks=${webHooks.map { it.toLoggable() }}," +
                ")"
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
