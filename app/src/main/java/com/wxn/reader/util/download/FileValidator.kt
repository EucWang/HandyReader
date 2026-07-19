package com.wxn.reader.util.download

import com.wxn.base.util.Logger
import java.io.File
import java.io.RandomAccessFile

object FileValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val reason: String? = null
    )

    private const val TAG = "FileValidator"

    fun validate(file: File, extension: String, expectedSize: Long? = null): ValidationResult {
        if (!file.exists()) {
            return ValidationResult(false, "File does not exist")
        }

        if (file.length() == 0L) {
            return ValidationResult(false, "File is empty (0 bytes)")
        }

//        expectedSize?.let { expected ->
//            if (expected > 0 && file.length() != expected) {
//                return ValidationResult(
//                    false,
//                    "File size mismatch: expected=$expected, actual=${file.length()}"
//                )
//            }
//        }

        val magicResult = validateMagicBytes(file, extension)
        if (!magicResult.isValid) {
            Logger.w("FileValidator::validate: magic bytes validation failed: ${magicResult.reason},file=${file.absolutePath}")
            return magicResult
        }

        return ValidationResult(true)
    }

    private fun validateMagicBytes(file: File, extension: String): ValidationResult {
        return when (extension.lowercase()) {
            "epub" -> validateEpub(file)
            "pdf" -> validatePdf(file)
            "mobi", "azw3" -> validateMobi(file)
            "mp3" -> validateMp3(file)
            "m4a", "m4b" -> validateMp4(file)
            "aac" -> validateAac(file)
            else -> ValidationResult(true)
        }
    }

    private fun validateEpub(file: File): ValidationResult {
        val magic = readBytes(file, 4)
        if (magic == null || !magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))) {
            Logger.e("$TAG: EPUB magic bytes mismatch for ${file.name}, " +
                    "got: ${magic?.map { String.format("%02X", it) }?.joinToString(" ") ?: "null"}")
            return ValidationResult(false, "Not a valid EPUB file (invalid ZIP header)")
        }
        return ValidationResult(true)
    }

    private fun validatePdf(file: File): ValidationResult {
        val magic = readBytes(file, 4)
        if (magic == null || !magic.contentEquals(byteArrayOf(0x25, 0x50, 0x44, 0x46))) {
            return ValidationResult(false, "Not a valid PDF file")
        }
        return ValidationResult(true)
    }

    private fun validateMobi(file: File): ValidationResult {
        val header = readBytes(file, 78)
        if (header == null) {
            return ValidationResult(false, "File too short for MOBI header")
        }
        val mobiMarker = "BOOKMOBI".toByteArray(Charsets.US_ASCII)
        val offset = 60
        if (header.size < offset + mobiMarker.size) {
            return ValidationResult(false, "MOBI header too short")
        }
        val slice = header.sliceArray(offset until offset + mobiMarker.size)
        if (!slice.contentEquals(mobiMarker)) {
            return ValidationResult(false, "Not a valid MOBI/AZW3 file")
        }
        return ValidationResult(true)
    }

    private fun validateMp3(file: File): ValidationResult {
        val magic = readBytes(file, 3)
        if (magic == null) return ValidationResult(false, "File too short")
        val isSyncWord = (magic[0] == 0xFF.toByte() &&
                (magic[1].toInt() and 0xE0) == 0xE0)
        val isId3 = magic[0] == 0x49.toByte() && magic[1] == 0x44.toByte() && magic[2] == 0x33.toByte()
        if (!isSyncWord && !isId3) {
            return ValidationResult(false, "Not a valid MP3 file")
        }
        return ValidationResult(true)
    }

    private fun validateMp4(file: File): ValidationResult {
        val header = readBytes(file, 12)
        if (header == null || header.size < 12) {
            return ValidationResult(false, "File too short for MP4 header")
        }
        val ftyp = "ftyp".toByteArray(Charsets.US_ASCII)
        val slice = header.sliceArray(4 until 8)
        if (!slice.contentEquals(ftyp)) {
            return ValidationResult(false, "Not a valid M4A/M4B file")
        }
        return ValidationResult(true)
    }

    private fun validateAac(file: File): ValidationResult {
        val magic = readBytes(file, 2)
        if (magic == null) return ValidationResult(false, "File too short")
        val isAac = (magic[0] == 0xFF.toByte() &&
                (magic[1].toInt() and 0xF0) == 0xF0)
        if (!isAac) {
            return ValidationResult(false, "Not a valid AAC file")
        }
        return ValidationResult(true)
    }

    private fun readBytes(file: File, count: Int): ByteArray? {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                if (raf.length() < count) return null
                val buffer = ByteArray(count)
                raf.readFully(buffer)
                buffer
            }
        } catch (e: Exception) {
            Logger.e("$TAG: Failed to read file bytes: ${file.absolutePath}: ${e.message}")
            null
        }
    }
}
