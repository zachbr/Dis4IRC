/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2020 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.discord

import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.Source
import net.dv8tion.jda.api.entities.MessageChannel

fun MessageChannel.asBridgeSource(): Source = Source(this.name, this.idLong, PlatformType.DISCORD)
