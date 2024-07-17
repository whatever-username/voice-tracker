package com.whatever.service;

import com.whatever.logDebug
import com.whatever.logError
import de.sciss.jump3r.lowlevel.LameEncoder
import de.sciss.jump3r.mp3.Lame
import jakarta.inject.Singleton
import java.io.File
import java.io.FileOutputStream
import javax.sound.sampled.AudioFormat
import kotlin.math.min

@Singleton
class Mp3Encoder {

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

    fun encodePcmToMp3(username: String, pcm: ByteArray): String? {
        val userDirectory = File("mp3/$username").apply { mkdirs() }
        val fileName = "${userDirectory.path}/${System.currentTimeMillis()}.mp3"

        logDebug("Creating mp3 file $fileName")

        return try {
            FileOutputStream(File(fileName), false).use { mp3 ->
                val buffer = ByteArray(encoder.pcmBufferSize)
                var currentPcmPosition = 0
                while (currentPcmPosition < pcm.size) {
                    val bytesToTransfer = min(buffer.size, pcm.size - currentPcmPosition)
                    val bytesWritten = encoder.encodeBuffer(pcm, currentPcmPosition, bytesToTransfer, buffer)
                    if (bytesWritten <= 0) break
                    mp3.write(buffer, 0, bytesWritten)
                    currentPcmPosition += bytesToTransfer
                }
            }
            fileName
        } catch (e: Throwable) {
            logError(e)
            null
        }
    }
}