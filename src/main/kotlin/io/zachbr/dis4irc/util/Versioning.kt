/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

import java.util.jar.Manifest

private const val JAR_PATH_TO_VERSIONING_INFO = "dis4irc-versioning.txt"

/**
 * Fetches and provides versioning information from the jar's manifest
 */
object Versioning {
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

        if (resources.hasMoreElements()) {
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
        }

        version = verOut
        gitHash = gitHashOut
        buildDate = dateOut
        sourceRepo = repoOut
    }
}
