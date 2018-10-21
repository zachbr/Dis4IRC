package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.mutator.api.Mutator

/**
 * Translates Discord's markdown formatting to the IRC formatting codes and vice versa
 */
class TranslateFormatting : Mutator {

    override fun mutate(message: String, source: Channel, sender: Sender): String? {
        return when (source.type) {
            Channel.Type.IRC -> formatForDiscord(message)
            Channel.Type.DISCORD -> formatForIrc(message)
        }
    }

    private fun formatForDiscord(message: String): String {
        TODO("Use forked version of txtmark with GFM and custom emitter support") // https://github.com/rjeschke/txtmark/pulls
    }

    private fun formatForIrc(message: String): String {
        TODO("Use forked version of txtmark with GFM and custom emitter support") // https://github.com/rjeschke/txtmark/pulls
    }

}
