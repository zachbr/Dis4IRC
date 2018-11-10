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

package io.zachbr.dis4irc.util

import java.util.jar.Manifest

private const val JAR_PATH_TO_VERSIONING_INFO = "dis4irc-versioning.txt"

/**
 * Fetches and provides versioning information from the jar's manifest
 */
class Versioning {
    /**
     * Gets the build version of this jar
     */
    val version: String
    /**
     * Gets the top of tree git hash this version was built against
     */
    val gitHash: String
    /**
     * Gets the build date of this jar
     */
    val buildDate: String

    /**
     * Gets the source repo of this project
     */
    val sourceRepo: String

    init {
        val resources = this.javaClass.classLoader.getResources(JAR_PATH_TO_VERSIONING_INFO)
        var verOut = "Unknown version"
        var gitHashOut = "Unknown Git Commit"
        var dateOut = "Unknown build date"
        var repoOut = "Unknown source repo"

        resources.nextElement().openStream().use {
            with(Manifest(it).mainAttributes) {
                if (getValue("Name") != "Dis4IRC") {
                    return@use
                }

                verOut = getValue("Version")
                gitHashOut = getValue("Git-Hash")
                dateOut = getValue("Build-Date")
                repoOut = getValue("Source-Repo")
            }
        }

        version = verOut
        gitHash = gitHashOut
        buildDate = dateOut
        sourceRepo = repoOut
    }
}
