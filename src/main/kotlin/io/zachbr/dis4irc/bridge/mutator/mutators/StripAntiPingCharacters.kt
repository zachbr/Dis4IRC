/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.IrcSource
import io.zachbr.dis4irc.bridge.message.PlatformMessage
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.bridge.pier.irc.IrcMessageFormatter

/**
 * Strips anti-ping characters.
 */
class StripAntiPingCharacters : Mutator {

    override fun mutate(message: PlatformMessage): Mutator.LifeCycle {
        if (message.source is IrcSource) {
            message.contents = message.contents.replace(IrcMessageFormatter.ANTI_PING_CHAR.toString(), "")
        }
        return Mutator.LifeCycle.CONTINUE
    }
}
