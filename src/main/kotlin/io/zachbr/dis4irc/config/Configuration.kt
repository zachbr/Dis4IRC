/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2024 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.config

import io.zachbr.dis4irc.logger
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.ConfigurationOptions
import org.spongepowered.configurate.hocon.HoconConfigurationLoader
import org.spongepowered.configurate.loader.HeaderMode
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

private const val HEADER = "Dis4IRC Configuration File"

/**
 * Responsible for interacting with the underlying configuration system
 */
class Configuration(pathIn: String) {

    /**
     * The config file for use
     */
    private val configPath: Path = Paths.get(pathIn)

    /**
     * Our configuration loader
     */
    private val configurationLoader: HoconConfigurationLoader = HoconConfigurationLoader.builder()
        .path(configPath)
        .defaultOptions(ConfigurationOptions.defaults().header(HEADER))
        .headerMode(HeaderMode.PRESET)
        .build()

    /**
     * Root configuration node
     */
    private var rootNode: CommentedConfigurationNode

    /**
     * Reads configuration file and prepares for use
     */
    init {
        try {
            rootNode = configurationLoader.load()
        } catch (ex: IOException) {
            logger.error("Could not load configuration! $ex")
            throw ex
        }
    }

    /**
     * Writes config back to file
     */
    internal fun saveConfig(): Boolean {
        try {
            configurationLoader.save(rootNode)
        } catch (ex: IOException) {
            logger.error("Could not save configuration file!")
            ex.printStackTrace()
            return false
        }

        return true
    }

    /**
     * Gets a child node from the root node
     */
    internal fun getNode(vararg keys: String): CommentedConfigurationNode {
        return rootNode.node(*keys)
    }
}
