/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge

import io.zachbr.dis4irc.config.makeDefaultNode
import io.zachbr.dis4irc.config.toBridgeConfiguration
import org.spongepowered.configurate.CommentedConfigurationNode

const val TEST_IRC_CHANNEL = "#bridge"
const val TEST_DISCORD_CHANNEL_ID = 123L

/**
 * Creates a standalone bridge for tests, with one channel mapping and IRC pointed at localhost.
 * Nothing is connected until a pier is started.
 */
fun testBridge(ircPort: Int = 6667, configure: (CommentedConfigurationNode) -> Unit = {}): Bridge {
    val node = CommentedConfigurationNode.root().node("bridges", "test")
    node.makeDefaultNode()
    node.node("irc", "server").set("127.0.0.1")
    node.node("irc", "port").set(ircPort.toString())
    node.node("irc", "use-ssl").set(false)
    node.node("irc", "nickname").set("bridge")
    node.node("irc", "anti-ping").set(false)
    node.node("irc", "use-nickname-colors").set(false)
    node.node("irc", "init-commands-list").set(emptyList<String>())
    node.node("discord-api-key").set("unused")
    node.node("channel-mappings").set(null)
    node.node("channel-mappings", TEST_DISCORD_CHANNEL_ID.toString()).set(TEST_IRC_CHANNEL)
    configure(node)
    return Bridge(null, node.toBridgeConfiguration())
}
