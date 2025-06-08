/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.api

import io.zachbr.dis4irc.bridge.message.PlatformMessage

/**
 * A mutator takes the given message contents and alters it in some way before returning it
 */
interface Mutator {
    /**
     * Called on a given message to mutate the contents
     *
     * @return how to proceed with the message's lifecycle
     */
    fun mutate(message: PlatformMessage): LifeCycle

    /**
     * Mutator Life Cycle control types
     */
    enum class LifeCycle {
        /**
         * Continue the lifecycle by passing this message onto the next
         */
        CONTINUE,
        /**
         * Stop the lifecycle and discard the message entirely
         */
        STOP_AND_DISCARD,
        /**
         * Stop the lifecycle and return the message as it exists currently
         */
        RETURN_EARLY
    }
}
