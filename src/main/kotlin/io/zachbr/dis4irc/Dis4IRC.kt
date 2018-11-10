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

package io.zachbr.dis4irc

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.config.Configuration
import io.zachbr.dis4irc.config.makeDefaultNode
import io.zachbr.dis4irc.config.toBridgeConfiguration
import io.zachbr.dis4irc.util.Versioning
import ninja.leaping.configurate.ConfigurationNode
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    Dis4IRC(args)
}

class Dis4IRC(args: Array<String>) {
    private var configPath: String = "config.hocon"
    private val bridgesByName = HashMap<String, Bridge>()

    init {
        parseArguments(args)

        logger.info("Dis4IRC v${versioning.version}-${versioning.gitHash}")
        logger.info("Built on ${versioning.buildDate}")
        logger.info("Source available at ${versioning.sourceRepo}")
        logger.info("Licensed under the GNU Affero General Public License v3")

        logger.info("Loading config from: $configPath")
        val config = Configuration(configPath)

        val debugNode = config.getNode("debug-logging")
        if (debugNode.isVirtual) {
            debugNode.value = false
            config.saveConfig()
        }

        if (debugNode.boolean) {
            val logContext = (LogManager.getContext(false) as LoggerContext)
            val logConfig = logContext.configuration
            logConfig.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).level = org.apache.logging.log4j.Level.DEBUG
            logContext.updateLoggers(logConfig)

            logger.debug("Debug logging enabled")
        }

        val bridgesNode = config.getNode("bridges")
        if (bridgesNode.isVirtual) {
            bridgesNode.setComment(
                "A list of bridges that Dis4IRC should start up\n" +
                        "Each bridge can bridge multiple channels between a single IRC and Discord Server"
            )

            bridgesNode.getNode("default").makeDefaultNode()
            config.saveConfig()
            logger.debug("Default config written to $configPath")
        }

        if (bridgesNode.hasMapChildren()) {
            bridgesNode.childrenMap.forEach { startBridge(it.value, config.getNode("bridges")) }
        } else {
            logger.error("No bridge configurations found!")
        }

        // re-save config now that bridges have init'd to hopefully update the file with any defaults
        config.saveConfig()

        Runtime.getRuntime().addShutdownHook(object : Thread() {
            override fun run() {
                for (bridge in bridgesByName.values) {
                    bridge.shutdown()
                }
            }
        })
    }

    /**
     * Initializes and starts a bridge instance
     */
    private fun startBridge(node: ConfigurationNode, bridgesNode: CommentedConfigurationNode) {
        logger.info("Starting bridge: ${node.key}")

        val bridgeConf = node.toBridgeConfiguration()
        val bridge = Bridge(bridgeConf, bridgesNode.getNode(bridgeConf.bridgeName))
        bridgesByName[bridgeConf.bridgeName] = bridge

        bridge.startBridge()
    }

    private fun parseArguments(args: Array<String>) {
        for ((i, arg) in args.withIndex()) {
            when (arg.toLowerCase()) {
                "-v","--version" -> {
                    printVersionInfo(minimal = true)
                    System.exit(0)
                }
                "--about" -> {
                    printVersionInfo(minimal = false)
                    System.exit(0)
                }
                "-c", "--config" -> {
                    if (args.size >= i + 2) {
                        configPath = args[i + 1]
                    }
                }
            }
        }
    }

    private fun printVersionInfo(minimal: Boolean = false) {
        // can't use logger, this has to be bare bones without prefixes or timestamps
        System.out.println("Dis4IRC v${versioning.version}-${versioning.gitHash}")
        if (minimal) {
            return
        }

        System.out.println("Built on ${versioning.buildDate}")
        System.out.println("Source available at ${versioning.sourceRepo}")
        System.out.println("Licensed under the GNU Affero General Public License v3")
    }

    companion object {
        /**
         * Static logger for use *only* during initialization, bridges have their own loggers
         */
        val logger: Logger = LoggerFactory.getLogger("init") ?: throw IllegalStateException("Unable to init logger!")
        val versioning = Versioning()
    }
}



