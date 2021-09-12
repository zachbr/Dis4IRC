/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.AllowedMentions
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.BOT_SENDER
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.Source
import io.zachbr.dis4irc.bridge.pier.Pier
import io.zachbr.dis4irc.util.replaceTarget
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger

private const val ZERO_WIDTH_SPACE = 0x200B.toChar()

class DiscordPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private val webhookMap = HashMap<String, WebhookClient>()
    private var botAvatarUrl: String? = null
    private lateinit var discordApi: JDA

    override fun start() {
        logger.info("Connecting to Discord API...")

        val discordApiBuilder = JDABuilder.createLight(bridge.config.discord.apiKey,
            GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EMOJIS)
            .setMemberCachePolicy(MemberCachePolicy.ALL) // so we can cache invisible members and ping people not online
            .enableCache(CacheFlag.EMOTE)
            .setActivity(Activity.of(Activity.ActivityType.DEFAULT, "IRC"))
            .addEventListeners(DiscordMsgListener(this))

        if (bridge.config.announceJoinsQuits) {
            discordApiBuilder.addEventListeners(DiscordJoinQuitListener(this))
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

        botAvatarUrl = discordApi.selfUser.avatarUrl

        logger.info("Discord Bot Invite URL: ${discordApi.getInviteUrl()}")
        logger.info("Connected to Discord!")
    }

    override fun onShutdown() {
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

        // convert a user tag to proper mentions
        for (member in guild.memberCache) {
            val mentionTrigger = "@${member.user.asTag}" // require @ prefix
            msg.contents = replaceTarget(msg.contents, mentionTrigger, member.asMention)
        }

        // convert effective nick name to proper mentions
        for (member in guild.memberCache) {
            val mentionTrigger = "@${member.effectiveName}" // require @ prefix
            msg.contents = replaceTarget(msg.contents, mentionTrigger, member.asMention)
        }

        // convert role use to proper mentions
        for (role in guild.roleCache) {
            if (!role.isMentionable) {
                continue
            }

            val mentionTrigger = "@${role.name}" // require @ prefix
            msg.contents = replaceTarget(msg.contents, mentionTrigger, role.asMention)
        }

        // convert emotes to show properly
        for (emote in guild.emoteCache) {
            val mentionTrigger = ":${emote.name}:"
            msg.contents = replaceTarget(msg.contents, mentionTrigger, emote.asMention)
        }

        // convert text channels to mentions
        for (guildChannel in guild.textChannelCache) {
            val mentionTrigger = "#${guildChannel.name}"
            msg.contents = replaceTarget(msg.contents, mentionTrigger, guildChannel.asMention)
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
        if (matchingUsers.isNotEmpty()) {
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
            .setAllowedMentions(
                AllowedMentions()
                    .withParseUsers(true)
                    .withParseRoles(true)
            )
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
            return snowflake == webhook.id
        }

        // nope
        return false
    }

    /**
     * Sends a message to the bridge for processing
     */
    fun sendToBridge(message: Message) {
        bridge.submitMessage(message)
    }

    /**
     * Gets the pinned messages from the specified discord channel or null if the channel cannot be found
     */
    fun getPinnedMessages(channelId: String): List<Message>? {
        val channel = getTextChannelBy(channelId) ?: return null
        val messages = channel.retrievePinnedMessages().complete()

        return messages.map { it.toBridgeMsg(logger) }.toList()
    }

    /**
     * Gets a text channel by snowflake ID or string
     */
    private fun getTextChannelBy(string: String): TextChannel? {
        val byId = discordApi.getTextChannelById(string)
        if (byId != null) {
            return byId
        }

        val byName = discordApi.getTextChannelsByName(string, false)
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

