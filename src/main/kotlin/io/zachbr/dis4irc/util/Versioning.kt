/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

import java.util.jar.Manifest

private const val JAR_PATH_TO_VERSIONING_INFO = "dis4irc-versioning.txt"

/**
 * Fetches and provides versioning information from the manifest specified in [JAR_PATH_TO_VERSIONING_INFO]
 */
object Versioning {
    /**
     * Gets the build version of this jar
     */
    val version: String

    /**
     * Gets the build suffix, this may be the git hash or a value specified at build time
     */
    val suffix: String

    /**
     * Gets the source repo of this project
     */
    val sourceRepo: String

    init {
        val resources = this.javaClass.classLoader.getResources(JAR_PATH_TO_VERSIONING_INFO)
        var verOut = "Unknown version"
        var suffixOut = "Unknown suffix"
        var repoOut = "Unknown source repo"

        if (resources.hasMoreElements()) {
            resources.nextElement().openStream().use {
                with(Manifest(it).mainAttributes) {
                    if (getValue("Name") != "Dis4IRC") {
                        return@use
                    }

                    verOut = getValue("Version")
                    suffixOut = getValue("Suffix")
                    repoOut = getValue("Source-Repo")
                }
            }
        }

        version = verOut
        suffix = suffixOut
        sourceRepo = repoOut
    }
}
