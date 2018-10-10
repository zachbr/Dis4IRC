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

import java.io.IOException
import java.util.jar.Manifest

fun main(args: Array<String>) {
    Dis4IRC().startup(args)
}

class Dis4IRC {
    internal val version: String
    private val sourceRepo: String

    init {
        val versionSource = getFromManifest()
        version = versionSource.first
        sourceRepo = versionSource.second
    }

    internal fun startup(args: Array<String>) {
        System.out.println("Starting Dis4IRC v$version")
        System.out.println("Licensed under the AGPLv3")
        System.out.println("Source available at $sourceRepo")
        System.out.println()
        args.forEach { a -> System.out.println(a) }
    }

    private fun getFromManifest(): Pair<String, String> {
        val resources = this::javaClass.get().classLoader.getResources("META-INF/MANIFEST.MF")
        while (resources.hasMoreElements()) {
            try {
                val manifest = Manifest(resources.nextElement().openStream())
                val ours = manifest.mainAttributes.getValue("Name") == "Dis4IRC"

                if (ours) {
                    val version = manifest.mainAttributes.getValue("Version")
                    val sourceRepo = manifest.mainAttributes.getValue("Source-Repo")
                    return Pair(version, sourceRepo)
                }
            } catch (ignored: IOException) {
            }
        }

        return Pair("Unknown Version", "Unknown Source Repo")
    }
}



