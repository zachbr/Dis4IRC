/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import java.util.regex.Pattern

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
    val channelMappings: List<ChannelMapping>
)

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
)
