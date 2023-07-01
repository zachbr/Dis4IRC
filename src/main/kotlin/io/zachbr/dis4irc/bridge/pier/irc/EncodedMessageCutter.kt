/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2023 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import org.kitteh.irc.client.library.util.Cutter
import org.kitteh.irc.client.library.util.Sanity
import java.nio.charset.Charset

/**
 * A version of KIRC's Cutter ported to Kotlin and adapted to track the encoded size of the output
 * rather than just the character count.
 *
 * KittehIRCClientLib Cutter.java
 * https://github.com/KittehOrg/KittehIRCClientLib/blob/46b5795227c075b39c98b57c5a490a9009c6d52d/src/main/java/org/kitteh/irc/client/library/util/Cutter.java#L41-L79
 *
 * @param encoding the encoding you expect the remote server to be using (seems to be nearly always UTF-8, but not required to be).
 */
class EncodedMessageCutter(private val encoding: Charset) : Cutter {

    override fun split(message: String, size: Int): MutableList<String> {
        Sanity.nullCheck(message, "Message")
        Sanity.truthiness(size > 0, "Size must be positive")
        val listOut = ArrayList<String>()
        if (message.toByteArray(encoding).size <= size) {
            listOut.add(message)
            return listOut
        }

        val builder = StringBuilder(size)
        for (word in message.split(" ")) {
            var word = word
            var wordEncodedLen = word.toByteArray(encoding).size
            var builderEncodedLen = builder.toString().toByteArray(encoding).size
            val potentialSpaceLen = if (builder.isEmpty()) { 0 } else { 1 }
            if (builderEncodedLen + wordEncodedLen + potentialSpaceLen > size) {
                if (wordEncodedLen > size && builderEncodedLen + 1 < size) {
                    if (builder.isNotEmpty()) {
                        builder.append(' ')
                        builderEncodedLen += 1
                    }

                    val cut = size - builderEncodedLen
                    builder.append(word, 0, cut)
                    //builderEncodedLen = builder.toString().toByteArray(encoding).size // recalculate because change
                    word = word.substring(cut)
                    wordEncodedLen = word.toByteArray(encoding).size // recalculate because change
                }

                listOut.add(builder.toString().trim())
                builder.setLength(0)
                //builderEncodedLen = 0
                while (wordEncodedLen > size) {
                    listOut.add(word.substring(0, size))
                    word = word.substring(size)
                    wordEncodedLen = word.toByteArray(encoding).size // recalculate because change
                }
            }

            if (builder.isNotEmpty()) {
                builder.append(' ')
            }
            builder.append(word)
        }

        if (builder.isNotEmpty()) {
            listOut.add(builder.toString().trim())
        }

        return listOut
    }
}
