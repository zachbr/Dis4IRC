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

        discordApi = JDABuilder()
            .setToken(config.discordApiKey)
            .setGame(Game.of(Game.GameType.DEFAULT, "IRC"))
            .addEventListener(DiscordListener(this))
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
            logger.error("Unable to get a discord channel by name for: $targetChan")
            return
        }

        val webhook = webhookMap[targetChan]
        val guild = channel.guild

        // convert mentions and emotes
        val builder = StringBuilder()
        for (word in msg.contents.split(" ")) {
            val mentions = guild.getMembersByEffectiveName(word.removePrefix("@"), true)

            // todo - this is nasty, clean it up
            // don't want it to trigger all the time, only if they're trying for an emote
            val emotes = if (word.startsWith(":") && word.endsWith(":")) {
                guild.getEmotesByName(word.replace(":", ""), true)
            } else {
                Collections.emptyList()
            }

            when {
                mentions.isNotEmpty() -> builder.append(mentions.first().asMention)
                emotes.isNotEmpty() -> builder.append(emotes.first().asMention)
                else -> builder.append(word)
            }

            builder.append(" ")
        }

        if (builder.isNotEmpty()) {
            msg.contents = builder.toString()
        }

        if (webhook != null) {
            sendMessageWebhook(webhook, msg)
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

    private fun sendMessageWebhook(webhook: WebhookClient, msg: Message) {
        // try and get avatar for matching user
        var avatarUrl: String? = null
        val matchingUsers = discordApi?.getUsersByName(msg.sender.displayName, true)
        if (matchingUsers != null && matchingUsers.isNotEmpty()) {
            avatarUrl = matchingUsers.first().avatarUrl
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

    private fun getTextChannelBy(string: String): TextChannel? {
        return discordApi?.getTextChannelById(string) ?: discordApi?.getTextChannelsByName(string, false)?.first()
    }
}
