package com.whatever.service;

import com.whatever.log
import de.sciss.jump3r.lowlevel.LameEncoder
import de.sciss.jump3r.mp3.Lame
import jakarta.inject.Singleton
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.sound.sampled.AudioFormat
import kotlin.math.min
import org.slf4j.LoggerFactory

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

    fun encodePcmToMp3(username: String, pcm: ByteArray): String {
        val fileName = "mp3/$username/${System.currentTimeMillis()}.mp3"
        try {
            val userDirectory = File("mp3/$username")
            if (!userDirectory.exists()) {
                userDirectory.mkdirs()
            }
            FileOutputStream(File(fileName), false).use { mp3 ->
                val buffer = ByteArray(encoder.pcmBufferSize)
                var bytesToTransfer = min(buffer.size.toDouble(), pcm.size.toDouble()).toInt()
                var bytesWritten: Int
                var currentPcmPosition = 0
                while (0 < encoder.encodeBuffer(pcm, currentPcmPosition, bytesToTransfer, buffer)
                        .also { bytesWritten = it }
                ) {
                    currentPcmPosition += bytesToTransfer
                    bytesToTransfer =
                        min(buffer.size.toDouble(), (pcm.size - currentPcmPosition).toDouble()).toInt()
                    mp3.write(buffer, 0, bytesWritten)
                }
            }
        } catch (e: IOException) {
            log("IOException: ${e.message}")
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            log("OutOfMemoryError: ${e.message}")
            e.printStackTrace()
        }
        return fileName
    }
}