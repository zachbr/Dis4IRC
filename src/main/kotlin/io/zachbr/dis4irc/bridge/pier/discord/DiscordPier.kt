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

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.BridgeConfiguration
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

class DiscordPier(private val bridge: Bridge) : Pier {
    internal val logger: Logger = bridge.logger
    private val webhookMap = HashMap<String, WebhookClient>()

    private var discordApi: JDA? = null
    private var botName: String? = null
    private var botAvatarUrl: String? = null

    override fun init(config: BridgeConfiguration) {
        logger.info("Connecting to Discord API...")

        val discordApiBuilder = JDABuilder()
            .setToken(config.discordApiKey)
            .setGame(Game.of(Game.GameType.DEFAULT, "IRC"))
            .addEventListener(DiscordMsgListener(this))

        if (config.announceJoinsQuits) {
            discordApiBuilder.addEventListener(DiscordJoinQuitListener(this))
        }

        discordApi = discordApiBuilder
            .build()
            .awaitReady()

        // init webhooks
        if (config.discordWebHooks.isNotEmpty()) {
            logger.info("Initializing Discord webhooks")

            for (hook in config.discordWebHooks) {
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

        // load bot name and avatar url
        botName = discordApi?.selfUser?.name
        botAvatarUrl = discordApi?.selfUser?.avatarUrl

        logger.info("Discord Bot Invite URL: ${discordApi?.asBot()?.getInviteUrl()}")
        logger.info("Connected to Discord!")
    }

    override fun shutdown() {
        discordApi?.shutdownNow()

        for (client in webhookMap.values) {
            client.close()
        }
    }

    override fun sendMessage(targetChan: String, msg: Message) {
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

        val prefix = if (msg.originatesFromBridgeItself()) "" else "<${msg.sender.displayName}> "

        discordChannel.sendMessage("$prefix${msg.contents}").queue()
    }

    private fun sendMessageWebhook(guild: Guild, webhook: WebhookClient, msg: Message) {
        // try and get avatar for matching user
        var avatarUrl: String? = null
        val matchingUsers = guild.getMembersByEffectiveName(msg.sender.displayName, true)
        if (matchingUsers != null && matchingUsers.isNotEmpty()) {
            avatarUrl = matchingUsers.first().user.avatarUrl
        }

        var senderName = msg.sender.displayName
        // if sender is command, use bot's actual name and avatar if possible
        if (msg.sender == BOT_SENDER) {
            senderName = botName ?: senderName
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
        if (snowflake == discordApi?.selfUser?.idLong) {
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
        val byId = discordApi?.getTextChannelById(string)
        if (byId != null) {
            return byId
        }

        val byName = discordApi?.getTextChannelsByName(string, false) ?: return null
        return if (byName.isNotEmpty()) byName.first() else null
    }

    /**
     * Given a string, find the target and replace it, optionally requiring whitespace separation to replace
     */
    private fun replaceTarget(base: String, target: String, replacement: String, requireSeparation: Boolean = true): String {
        // find where and if the target string is used
        val startingIndex = base.indexOf(target, ignoreCase = true)
        if (startingIndex != -1) {
            fun isWhiteSpace(i: Int): Boolean {
                return i == -1 || i == base.length || !requireSeparation || base[i].isWhitespace()
            }

            // calc prior and post indexes
            val priorIndex = startingIndex - 1
            val postIndex = startingIndex + target.length

            if (isWhiteSpace(priorIndex) && isWhiteSpace(postIndex)) {
                return base.replace(target, replacement, true)
            }
        }

        return base
    }
}
