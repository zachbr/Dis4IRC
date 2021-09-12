/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) 2018-2021 Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.message.PlatformType
import io.zachbr.dis4irc.bridge.message.Sender
import io.zachbr.dis4irc.bridge.message.Source
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.element.User
import java.util.Optional

fun Channel.asBridgeSource(): Source = Source(this.name, null, PlatformType.IRC)
fun User.asBridgeSender(): Sender = Sender(this.nick, this.realName.orElse(this.nick), null, this.account.toNullable())
fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)
