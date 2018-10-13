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

package io.zachbr.dis4irc.config

import io.zachbr.dis4irc.Dis4IRC.Static.logger
import ninja.leaping.configurate.ConfigurationOptions
import ninja.leaping.configurate.commented.CommentedConfigurationNode
import ninja.leaping.configurate.commented.SimpleCommentedConfigurationNode
import ninja.leaping.configurate.hocon.HoconConfigurationLoader
import ninja.leaping.configurate.loader.HeaderMode
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class Configuration(pathIn: String) {

    /**
     * The config file for use
     */
    private val configPath: Path = Paths.get(pathIn)

    /**
     * Our configuration loader
     */
    private val configurationLoader: HoconConfigurationLoader = HoconConfigurationLoader.builder()
        .setPath(configPath)
        .setHeaderMode(HeaderMode.PRESET)
        .build()

    /**
     * Root configuration node
     */
    private var rootNode: SimpleCommentedConfigurationNode

    /**
     * Reads configuration file and prepares for use
     */
    init {
        try {
            rootNode = SimpleCommentedConfigurationNode.root(ConfigurationOptions.defaults().setHeader(getHeader()))
        } catch (ex: IOException) {
            logger.error("Could not load configuration! $ex")
            throw ex
        }
    }

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
        return rootNode.getNode(*keys)
    }

    internal fun filePresent(): Boolean {
        return Files.exists(configPath)
    }

    /**
     * Gets the super serious and entirely professional header for the config
     */
    private fun getHeader(): String {
        return "Dis4IRC Configuration File"
    }
}
