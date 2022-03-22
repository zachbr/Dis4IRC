/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2022 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.mutator.api.Mutator
import io.zachbr.dis4irc.bridge.pier.irc.ANTI_PING_CHAR

/**
 * Strips anti-ping characters.
 */
class StripAntiPingCharacters : Mutator {

    override fun mutate(message: Message): Mutator.LifeCycle {
        if (message.source.type == PlatformType.IRC) {
            message.contents = message.contents.replace(ANTI_PING_CHAR.toString(), "")
        }
        return Mutator.LifeCycle.CONTINUE
    }
}
