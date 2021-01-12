/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc

import io.zachbr.dis4irc.bridge.Bridge
import io.zachbr.dis4irc.config.Configuration
import io.zachbr.dis4irc.config.makeDefaultNode
import io.zachbr.dis4irc.config.toBridgeConfiguration
import io.zachbr.dis4irc.util.AtomicFileUtil
import io.zachbr.dis4irc.util.Versioning
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.json.JSONObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    Dis4IRC(args)
}
val logger: Logger = LoggerFactory.getLogger("init") ?: throw IllegalStateException("Unable to init logger!")
val SAVED_DATA_PATH: Path = Paths.get("./bridge-data.dat")

class Dis4IRC(args: Array<String>) {
    private var configPath: String = "config.hocon"
    private val bridgesByName = HashMap<String, Bridge>()
    private val bridgesInErr = HashMap<String, Bridge>()
    private var shuttingDown = false

    init {
        parseArguments(args)

        logger.info("Dis4IRC v${Versioning.version}-${Versioning.gitHash}")
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

        if (bridgesNode.isMap) {
            bridgesNode.childrenMap.forEach { startBridge(it.value) }
        } else {
            logger.error("No bridge configurations found!")
        }

        kotlin.runCatching { loadBridgeData(SAVED_DATA_PATH) }.onFailure {
            logger.error("Unable to load bridge data: $it")
            it.printStackTrace()
        }

        // re-save config now that bridges have init'd to hopefully update the file with any defaults
        config.saveConfig()

        Runtime.getRuntime().addShutdownHook(Thread {
            shuttingDown = true
            kotlin.runCatching { saveBridgeData(SAVED_DATA_PATH) }.onFailure {
                logger.error("Unable to save bridge data: $it")
                it.printStackTrace()
            }
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
            bridgesInErr[name] = bridge
        }

        if (!shuttingDown && bridgesByName.size == 0) {
            logger.info("No bridges running - Exiting")

            kotlin.runCatching { saveBridgeData(SAVED_DATA_PATH) }.onFailure {
                logger.error("Unable to save bridge data: $it")
                it.printStackTrace()
            }

            val anyErr = bridgesInErr.isNotEmpty()
            val exitCode = if (anyErr) 1 else 0
            if (anyErr) {
                val errBridges: String = bridgesInErr.keys.joinToString(", ")
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

        println("Source available at ${Versioning.sourceRepo}")
        println("Available under the MIT License")
    }

    private fun loadBridgeData(path: Path) {
        if (Files.notExists(path)) return

        logger.debug("Loading bridge data from $path")
        val json: JSONObject = Files.newInputStream(path, StandardOpenOption.READ).use {
            val compressedIn = GZIPInputStream(it)
            val textIn = InputStreamReader(compressedIn)
            return@use JSONObject(textIn.readText())
        }

        bridgesByName.forEach { entry ->
            if (json.has(entry.key)) {
                entry.value.readSavedData(json.getJSONObject((entry.key)))
            }
        }
    }

    private fun saveBridgeData(path: Path) {
        logger.debug("Saving bridge data to $path")
        val json = JSONObject()

        val bridges = TreeSet(Comparator { b1: Bridge, b2: Bridge -> // maintain consistent order
            return@Comparator b1.config.bridgeName.compareTo(b2.config.bridgeName)
        })
        bridges.addAll(bridgesByName.values)
        bridges.addAll(bridgesInErr.values)
        for (bridge in bridges) {
            json.put(bridge.config.bridgeName, bridge.persistData(JSONObject()))
        }

        AtomicFileUtil.writeAtomic(path) {
            val compressedOut = GZIPOutputStream(it)
            val textOut = OutputStreamWriter(compressedOut, "UTF-8")
            textOut.write(json.toString())
            // seeing some oddities with IJ's run config, these are probably not needed
            textOut.flush()
            compressedOut.close()
        }
    }

    private fun setLoggingLevel(level: Level) {
        val logContext = LogManager.getContext(false) as LoggerContext
        val logConfig = logContext.configuration
        logConfig.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).level = level
        logContext.updateLoggers(logConfig)

        logger.debug("Log level set to $level")
    }
}


