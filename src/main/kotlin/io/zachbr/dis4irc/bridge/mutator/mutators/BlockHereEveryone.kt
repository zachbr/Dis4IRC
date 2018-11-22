/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.mutator.mutators

import io.zachbr.dis4irc.bridge.message.Message
import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.mutator.api.Mutator

/**
 * Blocks @here and @everyone from being sent from IRC to Discord
 */
class BlockHereEveryone : Mutator {

    override fun mutate(message: Message): Mutator.LifeCycle {
        // only block from IRC -> Discord, allow them the other way around
        if (message.source.type != PlatformType.IRC) {
            return Mutator.LifeCycle.CONTINUE
        }

        var out = message.contents

        out = out.replace("@everyone", "at-everyone")
        out = out.replace("@here", "at-here")

        message.contents = out

        return Mutator.LifeCycle.CONTINUE
    }
}
