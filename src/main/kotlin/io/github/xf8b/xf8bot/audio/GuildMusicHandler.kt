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

package io.github.xf8b.xf8bot.audio

import com.github.benmanes.caffeine.cache.Caffeine
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.channel.MessageChannel
import io.github.xf8b.xf8bot.util.and
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

class GuildMusicHandler(
        val guildId: Snowflake,
        val audioPlayerManager: AudioPlayerManager,
        var messageChannel: MessageChannel
) {
    private val audioPlayer: AudioPlayer = audioPlayerManager.createPlayer()
    val lavaPlayerAudioProvider: LavaPlayerAudioProvider = LavaPlayerAudioProvider(audioPlayer)
    val musicTrackScheduler: MusicTrackScheduler = MusicTrackScheduler(audioPlayer, ::messageChannel) {
        it.subscribe()
    }

    init {
        audioPlayer.addListener(musicTrackScheduler.createListener())
    }

    companion object {
        private val CACHE = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build<Triple<Snowflake, AudioPlayerManager, MessageChannel>, GuildMusicHandler> {
                    GuildMusicHandler(it.first, it.second, it.third)
                }

        @JvmStatic
        fun getMusicHandler(guildId: Snowflake, audioPlayerManager: AudioPlayerManager, messageChannel: MessageChannel): GuildMusicHandler =
                CACHE.get(guildId and audioPlayerManager and messageChannel)!!
                        .also { it.messageChannel = messageChannel }
    }

    fun playYoutubeVideo(identifier: String): Mono<Void> = Mono.fromRunnable {
        audioPlayerManager.loadItemOrdered(guildId, identifier, musicTrackScheduler)
    }

    fun setVolume(volume: Int): Mono<Void> = Mono.fromRunnable {
        audioPlayer.volume = volume
    }

    fun isPaused() = audioPlayer.isPaused

    fun setPaused(paused: Boolean): Mono<Void> = Mono.fromRunnable {
        audioPlayer.isPaused = paused
    }

    fun skip(amountToGoForward: Int): Mono<Void> = Mono.fromRunnable {
        musicTrackScheduler.playNextAudioTrack(amountToGoForward)
    }
}