package com.wxn.base.util

import java.io.File
import java.io.FileOutputStream

object WavFileUtil {

    /**
     * 生成静音 WAV 文件（内存安全，支持超长时长）
     *
     * @param filePath       输出文件路径
     * @param durationSec    时长（秒），必须 > 0
     * @param sampleRate     采样率（Hz），常用 8000, 16000, 44100
     * @param channels       声道数，1=单声道，2=立体声
     * @param bitsPerSample  位深，通常为 16
     * @param chunkDurationSec  内部写缓冲块对应的时长（秒），建议 0.1 ~ 2.0，平衡内存与 I/O
     */
    fun generateSilenceWav(
        filePath: String,
        durationSec: Int,
        sampleRate: Int = 44100, // 8000, 16000, 44100
        channels: Short = 1,
        bitsPerSample: Short = 16, // 8
        chunkDurationSec: Double = 0.5
    ) : Boolean {
        require(durationSec > 0) { "durationSec must be positive" }
        require(bitsPerSample % 8 == 0) { "bitsPerSample must be multiple of 8" }
        require(channels in 1..8) { "channels should be 1..8" }

        val bytesPerSample = bitsPerSample / 8
        val frameSize = channels * bytesPerSample   // 每一帧的字节数
        val totalFrames = durationSec * sampleRate
        val dataLength = totalFrames * frameSize

        val tmpPath = "$filePath.tmp"
        val tmpFile = File(tmpPath)
        try {
            FileOutputStream(tmpFile).use { fos ->
                // 写入 WAV 头
                fos.write(buildWavHeader(dataLength, sampleRate, channels, bitsPerSample))

                // 分块写入静音数据
                val chunkFrames = (chunkDurationSec * sampleRate).toInt()
                val chunkBytes = chunkFrames * frameSize
                val silentChunk = ByteArray(chunkBytes)   // 全零

                var framesWritten = 0
                while (framesWritten < totalFrames) {
                    val remainingFrames = totalFrames - framesWritten
                    val framesToWrite = minOf(chunkFrames, remainingFrames)
                    val bytesToWrite = framesToWrite * frameSize
                    fos.write(silentChunk, 0, bytesToWrite)
                    framesWritten += framesToWrite
                }
            }
        } catch (ex: Exception) {
            tmpFile.delete()
            return false
        }
        return tmpFile.renameTo(File(filePath))
    }

    private fun buildWavHeader(
        dataLength: Int,
        sampleRate: Int,
        channels: Short,
        bitsPerSample: Short
    ): ByteArray {
        val header = ByteArray(44)
        val totalDataLen = dataLength + 36
        val byteRate = sampleRate * channels * (bitsPerSample / 8)

        // RIFF Chunk
        "RIFF".toByteArray().copyInto(header, 0)
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        "WAVE".toByteArray().copyInto(header, 8)

        // fmt Sub-chunk
        "fmt ".toByteArray().copyInto(header, 12)
        header[16] = 16 // fmt块大小 16
        header[20] = 1 // 音频格式 1代表PCM
        header[22] = channels.toByte()
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[34] = bitsPerSample.toByte()

        // data Sub-chunk
        "data".toByteArray().copyInto(header, 36)
        header[40] = (dataLength and 0xff).toByte()
        header[41] = ((dataLength shr 8) and 0xff).toByte()
        header[42] = ((dataLength shr 16) and 0xff).toByte()
        header[43] = ((dataLength shr 24) and 0xff).toByte()
        return header
    }
}