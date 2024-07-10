package com.whatever.service

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.whatever.IOScope
import com.whatever.RateLimiter
import com.whatever.factory.TelegramBot
import com.whatever.log
import io.micronaut.context.annotation.Value
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.audio.AudioReceiveHandler
import net.dv8tion.jda.api.audio.UserAudio
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel
import net.dv8tion.jda.api.managers.AudioManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Queue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@Singleton
class AudioHandler(
    private val telegramBot: Provider<TelegramBot>,
    private val mp3Encoder: Mp3Encoder,
    @Value("\${discord.usernames-to-record}") private val usernamesToRecord: List<String>
) : AudioReceiveHandler {

    lateinit var channel: AudioChannel
    lateinit var audioManager: AudioManager
    val playerManager: AudioPlayerManager = DefaultAudioPlayerManager()
    val player: AudioPlayer = playerManager.createPlayer()
    val audioSendHandler = AudioPlayerSendHandler(player)

    private val queues: MutableMap<String, ConcurrentLinkedQueue<ByteArray>> = ConcurrentHashMap()
    private var jobs: MutableMap<String, Job?> = mutableMapOf()
    private val delayTimeMillis = 500L
    private val tgMessageRateLimiter = RateLimiter(1000)
    private val audioLoadResultHandler = object : AudioLoadResultHandler {
        override fun trackLoaded(track: AudioTrack) {
            runCatching { player.playTrack(track) }.exceptionOrNull()?.let { log(it.message) }
        }

        override fun playlistLoaded(playlist: AudioPlaylist) {
            log(playlist)
        }

        override fun noMatches() {
        }

        override fun loadFailed(exception: FriendlyException) {
        }
    }

    private val jobsMutex = Mutex()

    override fun handleUserAudio(userAudio: UserAudio) {
        IOScope.launch {
            val username = userAudio.user.name
            if (username in usernamesToRecord) {
                try {
                    queues.computeIfAbsent(username) { ConcurrentLinkedQueue() }.add(userAudio.getAudioData(1.0))
                    delayFlushingRecordedCache(username)
                } catch (e: OutOfMemoryError) {
                    telegramBot.get().sendToMainAdmin("OutOfMemoryError: ${e.message}")
                    log(e.stackTraceToString())
                }
            }
        }


    }


    private suspend fun delayFlushingRecordedCache(username: String) {
        jobsMutex.withLock {
            jobs[username]?.cancel()
            jobs[username] = IOScope.launch {
                delay(delayTimeMillis)
                writeAudioData(username)
            }
        }
    }

    private fun writeAudioData(username: String) = IOScope.launch {
        val curQueue: Queue<ByteArray> = queues[username] ?: return@launch
        queues[username] = ConcurrentLinkedQueue()

        if (curQueue.sumOf { it.size } < 1000) {
            return@launch
        }

        val file = curQueue.let { queue ->
            ByteArrayOutputStream().use { tempOutputStream ->
                try {
                    queue.forEach { tempOutputStream.write(it) }
                    File(mp3Encoder.encodePcmToMp3(username, tempOutputStream.toByteArray()))
                } catch (e: IOException) {
                    with("IO error on writing file: ${e.message}") {
                        log(this)
                        telegramBot.get().sendToMainAdmin(this)
                    }
                    return@launch
                }
            }
        }
        try {
            tgMessageRateLimiter.execute {
                runCatching { telegramBot.get().sendAudioToTelegramChat(caption = username, file = file) }.exceptionOrNull()?.let {
                    with("Failure on sending audio to storage: ${it.message}") {
                        log(this)
                        telegramBot.get().sendToMainAdmin(this)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }


    override fun canReceiveUser() = true


    fun playMp3InDiscord(trackFile: String) {
        if (!audioManager.isConnected) {
            audioManager.openAudioConnection(channel)
        }
        playerManager.loadItem(trackFile, audioLoadResultHandler)
    }

    fun stopPlayingInDiscord() {
        player.stopTrack()
    }
}