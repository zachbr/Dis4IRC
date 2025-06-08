/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import club.minnced.discord.webhook.WebhookClient
import club.minnced.discord.webhook.WebhookClientBuilder
import club.minnced.discord.webhook.send.AllowedMentions
import club.minnced.discord.webhook.send.WebhookMessageBuilder
import club.minnced.discord.webhook.util.WebhookErrorHandler
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.message.BridgeMessage
import io.zachbr.dis4irc.bridge.message.BridgeSender
import io.zachbr.dis4irc.bridge.message.DiscordMessage
import io.zachbr.dis4irc.bridge.message.DiscordSource
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.message.PlatformSource
import io.zachbr.dis4irc.bridge.pier.Pier
import io.zachbr.dis4irc.util.replaceTarget
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Activity.ActivityType
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.slf4j.Logger
import java.time.Instant

private const val ZERO_WIDTH_SPACE = 0x200B.toChar()

class DiscordPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private val webhookMap = HashMap<String, WebhookClient>()
    private var botAvatarUrl: String? = null
    private lateinit var discordApi: JDA

    override fun start() {
        logger.info("Connecting to Discord API...")

        val intents = listOf(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EMOJIS_AND_STICKERS, GatewayIntent.MESSAGE_CONTENT)
        val discordApiBuilder = JDABuilder.createLight(bridge.config.discord.apiKey, intents)
            .setMemberCachePolicy(MemberCachePolicy.ALL) // so we can cache invisible members and ping people not online
            .enableCache(CacheFlag.EMOJI, CacheFlag.STICKER)
            .addEventListeners(DiscordMsgListener(this))
            .setStatus(getEnumFromString(bridge.config.discord.onlineStatus, "Unknown online-status specified"))

        if (bridge.config.discord.activityDesc.isNotBlank()) {
            val activityType: ActivityType = getEnumFromString(bridge.config.discord.activityType, "Unknown activity-type specified")
            discordApiBuilder.setActivity(Activity.of(activityType, bridge.config.discord.activityDesc, bridge.config.discord.activityUrl))
        }

        if (bridge.config.announceJoinsQuits) {
            discordApiBuilder.addEventListeners(DiscordJoinQuitListener(this))
        }

        discordApi = discordApiBuilder
            .build()
            .awaitReady()

        // init webhooks
        if (bridge.config.discord.webHooks.isNotEmpty()) {
            logger.info("Initializing Discord webhooks")
            val webhookErrorHandler = WebhookErrorHandler { client, message, throwable ->
                logger.error("Webhook ${client.id}: $message")
                throwable?.printStackTrace()
            }

            for (hook in bridge.config.discord.webHooks) {
                val webhook: WebhookClient
                try {
                    webhook = WebhookClientBuilder(hook.webhookUrl).build()
                } catch (ex: IllegalArgumentException) {
                    logger.error("Webhook for ${hook.discordChannel} with url ${hook.webhookUrl} is not valid!")
                    ex.printStackTrace()
                    continue
                }

                webhook.setErrorHandler(webhookErrorHandler)
                webhookMap[hook.discordChannel] = webhook
                logger.info("Webhook for ${hook.discordChannel} registered")
            }
        }

        botAvatarUrl = discordApi.selfUser.avatarUrl

        logger.info("Discord Bot Invite URL: ${discordApi.getInviteUrl()}")
        logger.info("Connected to Discord!")
    }

    override fun onShutdown() {
        // shutdown can be called when discord fails to init
        if (this::discordApi.isInitialized) {
            discordApi.shutdownNow()
        }

        for (client in webhookMap.values) {
            client.close()
        }
    }

    override fun sendMessage(targetChan: String, msg: BridgeMessage) {
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
        val platMessage = msg.message

        // make sure to replace clearly separated mentions first to not replace partial mentions
        replaceMentions(guild, platMessage, true)

        // replace mentions but don't require separation to find some previously missed, non-separated ones
        replaceMentions(guild, platMessage, false)

        // convert emotes to show properly
        for (emoji in guild.emojiCache) {
            val mentionTrigger = ":${emoji.name}:"
            platMessage.contents = replaceTarget(platMessage.contents, mentionTrigger, emoji.asMention)
        }

        // Discord won't broadcast messages that are just whitespace
        if (platMessage.contents.trim() == "") {
            platMessage.contents = "$ZERO_WIDTH_SPACE"
        }

        if (webhook != null) {
            sendMessageWebhook(guild, webhook, msg)
        } else {
            sendMessageOldStyle(channel, msg)
        }

        bridge.updateStatistics(msg, Instant.now())
    }

    private fun sendMessageOldStyle(discordChannel: TextChannel, bMessage: BridgeMessage) {
        if (!discordChannel.canTalk()) {
            logger.warn("Bridge cannot speak in ${discordChannel.name} to send message: $bMessage")
            return
        }

        val platMessage = bMessage.message
        val senderName = enforceSenderName(platMessage.sender.displayName)
        val prefix = if (bMessage.originatesFromBridgeItself()) "" else "<$senderName> "

        discordChannel.sendMessage("$prefix${platMessage.contents}").queue()
    }

    private fun sendMessageWebhook(guild: Guild, webhook: WebhookClient, bMessage: BridgeMessage) {
        val platMessage = bMessage.message
        val guildUser = getMemberByUserNameOrDisplayName(platMessage.sender.displayName, guild)
        var avatarUrl = guildUser?.effectiveAvatarUrl

        var senderName = enforceSenderName(platMessage.sender.displayName)
        // if sender is command, use bot's actual name and avatar if possible
        if (platMessage.sender == BridgeSender) {
            senderName = guild.getMember(discordApi.selfUser)?.effectiveName ?: senderName
            avatarUrl = botAvatarUrl ?: avatarUrl
        }

        val message = WebhookMessageBuilder()
            .setContent(platMessage.contents)
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
    fun isThisBot(source: PlatformSource, userSnowflake: Long): Boolean {
        if (source !is DiscordSource) {
            return false
        }

        // check against bot user directly
        if (userSnowflake == discordApi.selfUser.idLong) {
            return true
        }

        // check against webclients
        val webhook = webhookMap[source.channelId.toString()] ?: webhookMap[source.channelName]
        if (webhook != null) {
            return userSnowflake == webhook.id
        }

        // nope
        return false
    }

    /**
     * Sends a message to the bridge for processing
     */
    fun sendToBridge(message: DiscordMessage) {
        // try and resolve local snapshot mentions before they go across the bridge
        if (message.snapshots.isNotEmpty()) {
            for (snapshot in message.snapshots) {
                val textChannel = getTextChannelBy(message.source.channelId.toString()) ?: continue
                snapshot.contents = parseMentionableToNames(textChannel.guild, snapshot.contents, requireSeparation = true)
                snapshot.contents = parseMentionableToNames(textChannel.guild, snapshot.contents, requireSeparation = false)
            }
        }

        bridge.submitMessage(BridgeMessage(message))
    }

    /**
     * Gets the pinned messages from the specified discord channel or null if the channel cannot be found
     */
    fun getPinnedMessages(channelId: String): List<DiscordMessage>? {
        val channel = getTextChannelBy(channelId) ?: return null
        val messages = channel.retrievePinnedMessages().complete()

        return messages.map { it.toPlatformMessage(logger) }.toList()
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

    private fun replaceMentions(guild: Guild, msg: PlatformMessage, requireSeparation: Boolean) {
        // convert name use to proper mentions
        for (member in guild.memberCache) {
            val mentionTrigger = "@${member.effectiveName}" // require @ prefix
            msg.contents = replaceTarget(msg.contents, mentionTrigger, member.asMention, requireSeparation)
        }

        // convert role use to proper mentions
        for (role in guild.roleCache) {
            if (!role.isMentionable) {
                continue
            }

            val mentionTrigger = "@${role.name}" // require @ prefix
            msg.contents = replaceTarget(msg.contents, mentionTrigger, role.asMention, requireSeparation)
        }

        // convert text channels to mentions
        for (guildChannel in guild.textChannelCache) {
            val mentionTrigger = "#${guildChannel.name}"
            msg.contents = replaceTarget(msg.contents, mentionTrigger, guildChannel.asMention, requireSeparation)
        }
    }

    private fun parseMentionableToNames(guild: Guild, contents: String, requireSeparation: Boolean): String {
        var newContents = contents

        // convert name use to proper mentions
        for (member in guild.memberCache) {
            newContents = replaceTarget(newContents, member.asMention, member.effectiveName, requireSeparation)
        }

        // convert role use to proper mentions
        for (role in guild.roleCache) {
            if (!role.isMentionable) {
                continue
            }

            newContents = replaceTarget(newContents, role.asMention, role.name, requireSeparation)
        }

        // convert text channels to mentions
        for (guildChannel in guild.textChannelCache) {
            newContents = replaceTarget(newContents, guildChannel.asMention, guildChannel.name, requireSeparation)
        }

        return newContents;
    }

    private fun getMemberByUserNameOrDisplayName(name: String, guild: Guild, ignoreCase: Boolean = true): Member? {
        // check by username first
        var matchingUsers = guild.getMembersByName(name, ignoreCase)
        // if no results, check by their nickname instead
        if (matchingUsers.isEmpty()) {
            matchingUsers = guild.getMembersByNickname(name, ignoreCase)
        }
        // if we still don't have any results, fire off a findMembers call to look it up (and cache it for later)
        // this won't help us with this specific call (we don't really want to wait around for this task to come back),
        // but it will help us with future calls to this and other functions, so the next time they talk, we'll have it.
        if (matchingUsers.isEmpty()) {
            guild.findMembers { it.user.name.equals(name, ignoreCase) || it.nickname.equals(name, ignoreCase) }
                .onSuccess { logger.debug("Cached ${it.size} results for user lookup: $name") }
        }

        return matchingUsers.firstOrNull()
    }

    /**
     * Gets an enum from the given string or throw an IllegalArgumentException with the given error message
     */
    private inline fun <reified T: Enum<T>> getEnumFromString(userInput: String, errorMessage: String): T {
        val inputUpper = userInput.uppercase()
        for (poss in T::class.java.enumConstants) {
            if (poss.name.uppercase() == inputUpper) {
                return poss
            }
        }

        throw IllegalArgumentException("$errorMessage: $userInput")
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

