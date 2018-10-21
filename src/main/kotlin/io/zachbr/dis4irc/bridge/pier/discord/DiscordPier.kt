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

import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.bridge.BridgeConfiguration
import io.zachbr.dis4irc.bridge.command.COMMAND_SENDER
import io.zachbr.dis4irc.bridge.pier.Pier
import net.dv8tion.jda.core.JDA
import net.dv8tion.jda.core.JDABuilder
import net.dv8tion.jda.core.entities.Game
import net.dv8tion.jda.webhook.WebhookClient
import net.dv8tion.jda.webhook.WebhookClientBuilder
import net.dv8tion.jda.webhook.WebhookMessageBuilder
import org.slf4j.Logger
import java.util.concurrent.TimeUnit

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

    /**
     * Gets whether the given string is a snowflake id
     */
    private fun isId(value: String): Boolean {
        val long: Long? = value.toLongOrNull()
        return long != null
    }

    override fun shutdown() {
        discordApi?.shutdownNow()

        for (client in webhookMap.values) {
            client.close()
        }
    }

    override fun sendMessage(targetChan: String, msg: Message) {
        val webhook = webhookMap[targetChan]
        if (webhook == null) {
            sendMessageOldStyle(targetChan, msg)
        } else {
            sendMessageWebhook(webhook, msg)
        }

        logger.debug("Took approximately ${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - msg.timestamp)}ms to handle message out to Discord")
    }

    private fun sendMessageOldStyle(targetChan: String, msg: Message) {
        val discordChannel = discordApi?.getTextChannelById(targetChan)
        if (discordChannel == null) {
            logger.warn("Bridge is not present in Discord channel $targetChan!")
            return
        }

        if (!discordChannel.canTalk()) {
            logger.warn("Bridge cannot speak in ${discordChannel.name} to send message: $msg")
            return
        }

        val prefix = if (msg.sender == COMMAND_SENDER) { "" } else { "<${msg.sender.displayName}> " }

        discordChannel.sendMessage("$prefix${msg.contents}").complete()
    }

    private fun sendMessageWebhook(webhook: WebhookClient, msg: Message) {
        // try and get avatar for matching user
        var avatarUrl: String? = null
        val matchingUsers = discordApi?.getUsersByName(msg.sender.displayName, true)
        if (matchingUsers != null && matchingUsers.isNotEmpty()) {
            avatarUrl = matchingUsers[0].avatarUrl
        }

        var senderName = msg.sender.displayName
        // if sender is command, use bot's actual name and avatar if possible
        if (msg.sender == COMMAND_SENDER) {
            senderName = botName ?: senderName
            avatarUrl = botAvatarUrl ?: avatarUrl
        }

        val builder = WebhookMessageBuilder()
        builder.setContent(msg.contents)
        builder.setUsername(senderName)
        if (avatarUrl != null) {
            builder.setAvatarUrl(avatarUrl)
        }

        val message = builder.build()
        webhook.send(message)
    }

    /**
     * Checks if the message came from this bot
     */
    fun isThisBot(channel: Channel, snowflake: Long): Boolean {
        // check against bot user directly
        if (snowflake == discordApi?.selfUser?.idLong) {
            return true
        }

        // check against webclients
        val webhook = webhookMap[channel.discordId.toString()] ?: webhookMap[channel.name]
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
        bridge.handleMessage(message)
    }
}
