package io.zachbr.dis4irc.bridge.mutator.api

import io.zachbr.dis4irc.bridge.message.Channel
import io.zachbr.dis4irc.bridge.message.Sender

/**
 * A mutator takes the given message contents and alters it in some way before returning it
 */
interface Mutator {
    /**
     * Called on a given object to mutate the value
     */
    fun mutate(message: String, source: Channel, sender: Sender): String?
}
