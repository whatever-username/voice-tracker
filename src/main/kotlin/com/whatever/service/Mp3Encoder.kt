package com.whatever.service;

import com.whatever.factory.TelegramBot
import com.whatever.logDebug
import com.whatever.logError
import de.sciss.jump3r.lowlevel.LameEncoder
import de.sciss.jump3r.mp3.Lame
import jakarta.inject.Provider
import jakarta.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.AudioFormat
import kotlin.math.min

@Singleton
class Mp3Encoder(
    private val telegramBot: Provider<TelegramBot>
) {

    private val bitRate = 96
    private val channelMode = 2
    private val lameQuality: Int = Lame.STANDARD_FAST
    private val vbr = false
    private val audioFormat = AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        48000.0f,
        16,
        2,
        2,
        4F,
        true
    )

    private val encoder = LameEncoder(audioFormat, bitRate, channelMode, lameQuality, vbr)
    private val mp3Mutex = Mutex()

    suspend fun encodePcmToMp3(username: String, pcmAudioData: ByteArray): String? {
        mp3Mutex.withLock {
            val userDirectory = File("mp3/$username").apply { mkdirs() }
            val fileName = "${userDirectory.path}/${System.currentTimeMillis()}.mp3"

            logDebug("Creating mp3 file $fileName")

            return try {
                FileOutputStream(File(fileName), false).use { mp3 ->
                    val buffer = ByteArray(encoder.pcmBufferSize)
                    var currentPcmPosition = 0

                    while (currentPcmPosition < pcmAudioData.size) {
                        val bytesToTransfer = min(buffer.size, pcmAudioData.size - currentPcmPosition)
                        logDebug("Current PCM position: $currentPcmPosition, Bytes to transfer: $bytesToTransfer")

                        if (bytesToTransfer <= 0) {
                            logError("Bytes to transfer is zero or negative, breaking the loop")
                            break
                        }

                        val bytesWritten =
                            encoder.encodeBuffer(pcmAudioData, currentPcmPosition, bytesToTransfer, buffer)
                        logDebug("Bytes written: $bytesWritten")

                        if (bytesWritten < 0) {
                            logError("Bytes written is negative, breaking the loop")
                            break
                        }

                        mp3.write(buffer, 0, bytesWritten)
                        currentPcmPosition += bytesToTransfer
                    }
                }
                fileName
            } catch (e: ArrayIndexOutOfBoundsException) {
                logError("ArrayIndexOutOfBoundsException encountered: ${e.message}", telegramBot.get())
                null
            } catch (e: Throwable) {
                logError("Unexpected error: ${e.message}", telegramBot.get())
                null
            }
        }

    }
}