package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.mutator.api.Mutator

/**
 * Blocks @here and @everyone from being sent from IRC to Discord
 */
class BlockHereEveryone : Mutator {

    override fun mutate(message: String, source: Channel, sender: Sender): String? {
        if (source.type != Channel.Type.IRC) {
            return message
        }

        var out = message

        out = out.replace("@everyone", "at-everyone")
        out = out.replace("@here", "at-here")

        return out
    }
}
