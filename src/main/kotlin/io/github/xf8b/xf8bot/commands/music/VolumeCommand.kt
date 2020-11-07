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

package io.github.xf8b.xf8bot.commands.music

import com.google.common.collect.ImmutableList
import com.google.common.collect.Range
import discord4j.core.`object`.VoiceState
import discord4j.core.`object`.entity.Member
import io.github.xf8b.xf8bot.api.commands.AbstractCommand
import io.github.xf8b.xf8bot.api.commands.CommandFiredContext
import io.github.xf8b.xf8bot.api.commands.arguments.IntegerArgument
import io.github.xf8b.xf8bot.api.commands.flags.Flag
import io.github.xf8b.xf8bot.exceptions.ThisShouldNotHaveBeenThrownException
import io.github.xf8b.xf8bot.music.GuildMusicHandler
import reactor.core.publisher.Mono

class VolumeCommand : AbstractCommand(
    name = "\${prefix}volume",
    description = "Changes the volume of the music in the current VC.",
    commandType = CommandType.MUSIC,
    minimumAmountOfArgs = 1,
    arguments = ImmutableList.of(VOLUME)
) {
    companion object {
        private val VOLUME = IntegerArgument(
            name = "volume",
            index = Range.singleton(1),
            validityPredicate = { value ->
                try {
                    val level = value.toInt()
                    level in 0..400
                } catch (exception: NumberFormatException) {
                    false
                }
            },
            invalidValueErrorMessageFunction = { invalidValue ->
                try {
                    val level = invalidValue.toInt()
                    when {
                        level > 400 -> "The maximum volume is 400!"
                        level < 0 -> "The minimum volume is 1!"
                        else -> throw ThisShouldNotHaveBeenThrownException()
                    }
                } catch (exception: NumberFormatException) {
                    Flag.DEFAULT_INVALID_VALUE_ERROR_MESSAGE
                }
            }
        )
    }

    override fun onCommandFired(context: CommandFiredContext): Mono<Void> = Mono.defer {
        val guildId = context.guildId.get()
        val guildMusicHandler = GuildMusicHandler.get(
            guildId,
            context.xf8bot.audioPlayerManager,
            context.channel.block()!!
        )
        val volume = context.getValueOfArgument(VOLUME).get()
        context.client.voiceConnectionRegistry.getVoiceConnection(guildId)
            .flatMap {
                guildMusicHandler.setVolume(volume).then(context.channel.flatMap {
                    it.createMessage("Successfully set volume to $volume!")
                })
            }
            .switchIfEmpty(Mono.justOrEmpty(context.member)
                .flatMap(Member::getVoiceState)
                .flatMap(VoiceState::getChannel)
                .flatMap {
                    context.channel.flatMap { it.createMessage("I am not in a VC!") }
                }
                .switchIfEmpty(context.channel.flatMap {
                    it.createMessage("You are not in a VC!")
                })
            )
    }.then()
}