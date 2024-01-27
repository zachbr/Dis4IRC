/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2024 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.util

import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.function.Consumer

private const val MAX_RETRY_ATTEMPTS = 3
private const val WINDOWS_ATTR_HIDDEN = "dos:hidden"

object AtomicFileUtil {
    private val osIsWindows: Boolean

    init {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        osIsWindows = os.contains("win")
    }

    fun writeAtomic(destination: Path, consumer: Consumer<OutputStream>): Path {
        // resolve destination
        var targetFile = destination.toAbsolutePath()
        if (Files.exists(targetFile)) {
            try {
                targetFile = targetFile.toRealPath()
            } catch (ex: IOException) {
                // ignore
            }
        }

        // create and write temp file as hidden file
        val tempFile = Files.createTempFile(targetFile.parent, ".", "-" + targetFile.toAbsolutePath().fileName.toString() + ".tmp")
        // windows is a special snowflake
        if (osIsWindows) {
            setWindowsHiddenAttr(tempFile, true)
        }

        // accept data
        val dataOut = Files.newOutputStream(tempFile)
        consumer.accept(dataOut)
        dataOut.close()

        // atomic move
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (ex: AccessDeniedException) {
            // This is really only here to retry the move in the case that Windows (or some other process) holds a lock
            // on the file that prevents us from completing the move.
            for (attempt in 1..MAX_RETRY_ATTEMPTS) {
                try {
                    Thread.sleep(10 * attempt.toLong())
                    Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (ex: AccessDeniedException) {
                    if (attempt == MAX_RETRY_ATTEMPTS) {
                        throw ex
                    }
                }
            }
        } catch (ex: AtomicMoveNotSupportedException) {
            // Cthulhu help us, just move normally
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING)
        }

        // the main file is not hidden so we need to unset that attribute
        if (osIsWindows) {
            setWindowsHiddenAttr(targetFile, false)
        }

        return targetFile
    }

    private fun setWindowsHiddenAttr(path: Path, value: Boolean) {
        try {
            Files.setAttribute(path, WINDOWS_ATTR_HIDDEN, value)
        } catch (ex: IOException) {
            // ignore
        }
    }
}
