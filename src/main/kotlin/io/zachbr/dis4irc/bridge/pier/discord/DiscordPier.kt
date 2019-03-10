/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Source
import io.zachbr.dis4irc.bridge.pier.Pier
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.core.entities.Guild
import net.dv8tion.jda.core.entities.TextChannel
import net.dv8tion.jda.webhook.WebhookClient
import net.dv8tion.jda.webhook.WebhookClientBuilder
import net.dv8tion.jda.webhook.WebhookMessageBuilder
import org.slf4j.Logger
import java.util.*

private const val ZERO_WIDTH_SPACE = 0x200B.toChar()

class DiscordPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private val webhookMap = HashMap<String, WebhookClient>()
    private var botAvatarUrl: String? = null
    private lateinit var discordApi: JDA

    override fun start() {
        logger.info("Connecting to Discord API...")

        val discordApiBuilder = JDABuilder()
            .setToken(bridge.config.discord.apiKey)
            .setGame(Game.of(Game.GameType.DEFAULT, "IRC"))
            .addEventListener(DiscordMsgListener(this))

        if (bridge.config.announceJoinsQuits) {
            discordApiBuilder.addEventListener(DiscordJoinQuitListener(this))
        }

        discordApi = discordApiBuilder
            .build()
            .awaitReady()

        // init webhooks
        if (bridge.config.discord.webHooks.isNotEmpty()) {
            logger.info("Initializing Discord webhooks")

            for (hook in bridge.config.discord.webHooks) {
                val webhook: WebhookClient
                try {
                    webhook = WebhookClientBuilder(hook.webhookUrl).build()
                } catch (ex: IllegalArgumentException) {
                    logger.error("Webhook for ${hook.discordChannel} with url ${hook.webhookUrl} is not valid!")
                    ex.printStackTrace()
                    continue
                }

                webhookMap[hook.discordChannel] = webhook
                logger.info("Webhook for ${hook.discordChannel} registered")
            }
        }

        botAvatarUrl = discordApi.selfUser?.avatarUrl

        logger.info("Discord Bot Invite URL: ${discordApi.asBot()?.getInviteUrl()}")
        logger.info("Connected to Discord!")
    }

    override fun shutdown() {
        discordApi.shutdownNow()

        for (client in webhookMap.values) {
            client.close()
        }
    }

    override fun sendMessage(targetChan: String, msg: Message) {
        if (!this::discordApi.isInitialized) {
            logger.error("Discord Connection has not been initialized yet!")
            return
        }

        val channel = getTextChannelBy(targetChan)
        if (channel == null) {
            logger.error("Unable to get a discord channel for: $targetChan | Is bot present?")
            return
        }

        val webhook = webhookMap[targetChan]
        val guild = channel.guild

        // convert name use to proper mentions
        for (member in guild.members) {
            val mentionTrigger = "@${member.effectiveName}" // require @ prefix
            msg.contents = replaceTarget(msg.contents, mentionTrigger, member.asMention)
        }

        // convert emotes to show properly
        for (emote in guild.emotes) {
            val mentionTrigger = ":${emote.name}:"
            msg.contents = replaceTarget(msg.contents, mentionTrigger, emote.asMention, requireSeparation = false)
        }

        // Discord won't broadcast messages that are just whitespace
        if (msg.contents.trim() == "") {
            msg.contents = "$ZERO_WIDTH_SPACE"
        }

        if (webhook != null) {
            sendMessageWebhook(guild, webhook, msg)
        } else {
            sendMessageOldStyle(channel, msg)
        }

        val outTimestamp = System.nanoTime()
        bridge.updateStatistics(msg, outTimestamp)
    }

    private fun sendMessageOldStyle(discordChannel: TextChannel, msg: Message) {
        if (!discordChannel.canTalk()) {
            logger.warn("Bridge cannot speak in ${discordChannel.name} to send message: $msg")
            return
        }

        val senderName = enforceSenderName(msg.sender.displayName)
        val prefix = if (msg.originatesFromBridgeItself()) "" else "<$senderName> "

        discordChannel.sendMessage("$prefix${msg.contents}").queue()
    }

    private fun sendMessageWebhook(guild: Guild, webhook: WebhookClient, msg: Message) {
        // try and get avatar for matching user
        var avatarUrl: String? = null
        val matchingUsers = guild.getMembersByEffectiveName(msg.sender.displayName, true)
        if (matchingUsers != null && matchingUsers.isNotEmpty()) {
            avatarUrl = matchingUsers.first().user.avatarUrl
        }

        var senderName = enforceSenderName(msg.sender.displayName)
        // if sender is command, use bot's actual name and avatar if possible
        if (msg.sender == BOT_SENDER) {
            senderName = guild.getMember(discordApi.selfUser)?.effectiveName ?: senderName
            avatarUrl = botAvatarUrl ?: avatarUrl
        }

        val message = WebhookMessageBuilder()
            .setContent(msg.contents)
            .setUsername(senderName)
            .setAvatarUrl(avatarUrl)
            .build()

        webhook.send(message)
    }

    /**
     * Checks if the message came from this bot
     */
    fun isThisBot(source: Source, snowflake: Long): Boolean {
        // check against bot user directly
        if (snowflake == discordApi.selfUser.idLong) {
            return true
        }

        // check against webclients
        val webhook = webhookMap[source.discordId.toString()] ?: webhookMap[source.channelName]
        if (webhook != null) {
            return snowflake == webhook.idLong
        }

        // nope
        return false
    }

    /**
     * Sends a message to the bridge for processing
     */
    fun sendToBridge(message: io.zachbr.dis4irc.bridge.message.Message) {
        bridge.submitMessage(message)
    }

    /**
     * Gets a text channel by snowflake ID or string
     */
    private fun getTextChannelBy(string: String): TextChannel? {
        val byId = discordApi.getTextChannelById(string)
        if (byId != null) {
            return byId
        }

        val byName = discordApi.getTextChannelsByName(string, false) ?: return null
        return if (byName.isNotEmpty()) byName.first() else null
    }
}

private const val NICK_ENFORCEMENT_CHAR = "-"

/**
 * Ensures name is within Discord's requirements
 */
fun enforceSenderName(name: String): String {
    if (name.length < 2) {
        return NICK_ENFORCEMENT_CHAR + name + NICK_ENFORCEMENT_CHAR
    }

    if (name.length > 32) {
        return name.substring(0, 32)
    }

    return name
}

/**
 * Given a string, find the target and replace it, optionally requiring whitespace separation to replace
 */
fun replaceTarget(base: String, target: String, replacement: String, requireSeparation: Boolean = true): String {
    var out = base

    fun isWhiteSpace(i: Int): Boolean {
        return i == -1 || i == out.length || !requireSeparation || out[i].isWhitespace()
    }

    var start = out.indexOf(target, 0)
    while (start > -1) {
        val end = start + target.length
        val nextSearchStart = start + replacement.length

        if (isWhiteSpace(start - 1) && isWhiteSpace(end)) {
            out = out.replaceFirst(target, replacement)
        }

        start = out.indexOf(target, nextSearchStart)
    }

    return out
}
