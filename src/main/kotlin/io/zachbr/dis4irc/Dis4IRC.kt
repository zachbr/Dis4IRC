/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2019 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.config.Configuration
import io.zachbr.dis4irc.config.makeDefaultNode
import io.zachbr.dis4irc.config.toBridgeConfiguration
import io.zachbr.dis4irc.util.Versioning
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.stream.Collectors
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Dis4IRC(args)
}
val logger: Logger = LoggerFactory.getLogger("init") ?: throw IllegalStateException("Unable to init logger!")

class Dis4IRC(args: Array<String>) {
    private var configPath: String = "config.hocon"
    private val bridgesByName = HashMap<String, Bridge>()
    private val bridgesInErr = ArrayList<String>()
    private var shuttingDown = false

    init {
        parseArguments(args)

        logger.info("Dis4IRC v${Versioning.version}-${Versioning.gitHash}")
        logger.info("Built on ${Versioning.buildDate}")
        logger.info("Source available at ${Versioning.sourceRepo}")
        logger.info("Available under the MIT License")

        logger.info("Loading config from: $configPath")
        val config = Configuration(configPath)

        //
        // Logging
        //

        val logLevel = config.getNode("log-level")
        if (logLevel.isVirtual) {
            logLevel.setComment("Sets the minimum amount of detail sent to the log. Acceptable values are: " +
                    "ERROR, WARN, INFO, DEBUG, TRACE") // I see no reason to mention OFF, FATAL, or ALL explicitly
            logLevel.value = "INFO"
        }

        val legacyLogDebugNode = config.getNode("debug-logging")
        if (!legacyLogDebugNode.isVirtual) {
            if (legacyLogDebugNode.boolean) {
                logLevel.value = "DEBUG"
            }

            legacyLogDebugNode.value = null
        }

        val l4j = Level.getLevel(logLevel.string?.toUpperCase()) ?: throw IllegalArgumentException("Unknown log-level in config: ${logLevel.string}")
        setLoggingLevel(l4j)

        //
        // Bridges
        //

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

        // re-save config now that bridges have init'd to hopefully update the file with any defaults
        //config.saveConfig() // Can throw NFE as a result of a HOCON lib issue, see https://github.com/zachbr/Dis4IRC/issues/19

        Runtime.getRuntime().addShutdownHook(Thread {
            shuttingDown = true
            ArrayList(bridgesByName.values).forEach { it.shutdown() }
        })
    }

    /**
     * Initializes and starts a bridge instance
     */
    private fun startBridge(node: CommentedConfigurationNode) {
        logger.info("Starting bridge: ${node.key}")

        val bridgeConf = node.toBridgeConfiguration()
        val bridge = Bridge(this, bridgeConf)

        if (bridgesByName[bridgeConf.bridgeName] != null) {
            throw IllegalArgumentException("Cannot register multiple bridges with the same name!")
        }

        bridgesByName[bridgeConf.bridgeName] = bridge

        bridge.startBridge()
    }

    internal fun notifyOfBridgeShutdown(bridge: Bridge, inErr: Boolean) {
        val name = bridge.config.bridgeName
        bridgesByName.remove(name) ?: throw IllegalArgumentException("Unknown bridge: $name has shutdown, why wasn't it tracked?")

        if (inErr) {
            bridgesInErr.add(bridge.config.bridgeName)
        }

        if (!shuttingDown && bridgesByName.size == 0) {
            logger.info("No bridges running - Exiting")

            val anyErr = bridgesInErr.isNotEmpty()
            val exitCode = if (anyErr) 1 else 0
            if (anyErr) {
                val errBridges: String = bridgesInErr.stream().collect(Collectors.joining(", "))
                logger.warn("The following bridges exited in error: $errBridges")
            }

            exitProcess(exitCode)
        }
    }

    private fun parseArguments(args: Array<String>) {
        for ((i, arg) in args.withIndex()) {
            when (arg.toLowerCase()) {
                "-v","--version" -> {
                    printVersionInfo(minimal = true)
                    exitProcess(0)
                }
                "--about" -> {
                    printVersionInfo(minimal = false)
                    exitProcess(0)
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
        println("Dis4IRC v${Versioning.version}-${Versioning.gitHash}")
        if (minimal) {
            return
        }

        println("Built on ${Versioning.buildDate}")
        println("Source available at ${Versioning.sourceRepo}")
        println("Available under the MIT License")
    }
}

fun setLoggingLevel(level: Level) {
    val logContext = LogManager.getContext(false) as LoggerContext
    val logConfig = logContext.configuration
    logConfig.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).level = level
    logContext.updateLoggers(logConfig)

    logger.debug("Log level set to $level")
}



