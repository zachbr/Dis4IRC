/*
 * This file is part of Dis4IRC.
 *
 * Copyright (c) Dis4IRC contributors
 *
 * MIT License
 */

package io.zachbr.dis4irc.bridge.pier.irc

import io.zachbr.dis4irc.bridge.message.IrcSender
import io.zachbr.dis4irc.bridge.message.IrcSource
import org.kitteh.irc.client.library.element.Channel
import org.kitteh.irc.client.library.element.User
import java.util.Optional

fun Channel.asBridgeSource(): IrcSource = IrcSource(this.name)
fun User.asBridgeSender(): IrcSender = IrcSender(this.nick, this.account.toNullable())
fun <T : Any> Optional<T>.toNullable(): T? = this.orElse(null)
