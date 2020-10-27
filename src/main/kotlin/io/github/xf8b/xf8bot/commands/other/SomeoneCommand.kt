/*
 * Copyright (c) 2020 xf8b.
 *
 * This file is part of xf8bot.
 *
 * xf8bot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * xf8bot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with xf8bot.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.xf8b.xf8bot.commands

import io.github.xf8b.xf8bot.api.commands.AbstractCommand
import io.github.xf8b.xf8bot.api.commands.CommandFiredEvent
import reactor.core.publisher.Mono

class SomeoneCommand : AbstractCommand(
    name = "\${prefix}someone",
    description = "Pings a random person.",
    commandType = CommandType.OTHER
) {
    override fun onCommandFired(event: CommandFiredEvent): Mono<Void> = event.guild.flatMap { guild ->
        guild.requestMembers()
            .collectList()
            .map {
                it.shuffle()
                it[0]
            }
            .flatMap { member -> event.channel.flatMap { it.createMessage(member.nicknameMention) } }
            .then()
    }
}