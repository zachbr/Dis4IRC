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

import io.zachbr.dis4irc.Dis4IRC.Static.logger
import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.config.Configuration
import io.zachbr.dis4irc.config.makeDefaultNode
import io.zachbr.dis4irc.config.toBridgeConfiguration
import io.zachbr.dis4irc.util.Versioning
import ninja.leaping.configurate.ConfigurationNode
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
        val versioning = Versioning()

        logger.info("Starting Dis4IRC v${versioning.version}")
        logger.info("Built on ${versioning.buildDate}")
        logger.info("Source available at ${versioning.sourceRepo}")
        logger.info("Licensed under the GNU Affero General Public License v3")

        if (args.isNotEmpty()) {
            configPath = args[0] // todo better parsing
        }

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
            bridgesNode.childrenMap.forEach { startBridge(it.value) }
        } else {
            logger.error("No bridge configurations found!")
        }

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
    private fun startBridge(node: ConfigurationNode) {
        logger.info("Starting bridge: ${node.key}")

        val bridgeConf = node.toBridgeConfiguration()
        val bridge = Bridge(bridgeConf)
        bridgesByName[bridgeConf.bridgeName] = bridge

        bridge.startBridge()
    }

    object Static {
        /**
         * Static logger for use *only* during initialization, bridges have their own loggers
         */
        val logger: Logger = LoggerFactory.getLogger("init") ?: throw IllegalStateException("Unable to init logger!")
    }
}



