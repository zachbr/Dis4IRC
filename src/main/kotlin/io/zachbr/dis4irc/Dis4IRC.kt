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

import io.zachbr.dis4irc.config.Configuration
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.jar.Manifest

fun main(args: Array<String>) {
    Dis4IRC(args)
}

class Dis4IRC(private val args: Array<String>) {
    private val version: String
    private val buildDate: String
    private val configPath: String = "./config.hocon"

    internal val logger = LoggerFactory.getLogger(this::class.java)
    internal val config = Configuration(configPath, logger)

    init {
        val versionSource = getVersionAndBuildDate()
        version = versionSource.first
        buildDate = versionSource.second

        logger.info("Starting Dis4IRC v$version")
        logger.info("Built on $buildDate")
        logger.info("Source available at https://github.com/zachbr/Dis4IRC")
        logger.info("Licensed under the AGPLv3")
        logger.info("")

        if (!config.filePresent()) {
            config.saveConfig()
            logger.debug("Config file written to $configPath")
        }
    }

    private fun getVersionAndBuildDate(): Pair<String, String> {
        val resources = this::javaClass.get().classLoader.getResources("META-INF/MANIFEST.MF")
        while (resources.hasMoreElements()) {
            try {
                val manifest = Manifest(resources.nextElement().openStream())
                val ours = manifest.mainAttributes.getValue("Name") == "Dis4IRC"

                if (ours) {
                    val version = manifest.mainAttributes.getValue("Version")
                    val sourceRepo = manifest.mainAttributes.getValue("Build-Date")
                    return Pair(version, sourceRepo)
                }
            } catch (ignored: IOException) {
            }
        }

        return Pair("Unknown Version", "Unknown Source Repo")
    }
}



