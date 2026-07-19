package com.wxn.reader.domain.use_case.font

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.wxn.base.util.Logger
import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import com.wxn.reader.domain.repository.FontRepository
import com.wxn.reader.util.FontFamilyAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class ImportFontUseCase @Inject constructor(
    private val fontRepository: FontRepository,
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ImportFontUseCase"
        private const val IMPORT_PREFIX = "imported_"
    }

    data class ImportResult(
        val success: Boolean,
        val importedFamilies: List<String> = emptyList(),
        val errorMessage: String? = null
    )

    suspend fun importFontFile(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val fileName = getFileName(uri)
            if (fileName.isNullOrEmpty()) {
                return@withContext ImportResult(
                    false, errorMessage = "Cannot determine file name"
                )
            }

            if (!FontFamilyAnalyzer.isFontFile(fileName)) {
                return@withContext ImportResult(
                    false, errorMessage = "Not a font file: $fileName"
                )
            }

            val fileInfo = FontFamilyAnalyzer.analyze(fileName)
            val familyName = fileInfo.familyName
            val fontId = generateFontId(familyName)
            val dirSuffix = generateDirSuffix(familyName)

            val fontDir = File(File(context.filesDir, "fonts"), "$IMPORT_PREFIX$dirSuffix")
            try {
                fontDir.mkdirs()

                val extension = fileName.substringAfterLast('.', "ttf")
                val localFileName = "${fileInfo.variant}.$extension"
                val targetFile = File(fontDir, localFileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext ImportResult(false, errorMessage = "Cannot open file")

                if (!targetFile.exists() || targetFile.length() == 0L) {
                    fontDir.deleteRecursively()
                    return@withContext ImportResult(false, errorMessage = "File copy failed")
                }

                saveFontToDatabase(fontId, familyName, fontDir, fileInfo, localFileName, targetFile)
            } catch (e: Exception) {
                if (fontDir.exists() && fontDir.listFiles()?.isEmpty() == true) {
                    fontDir.deleteRecursively()
                }
                throw e
            }

            ImportResult(success = true, importedFamilies = listOf(familyName))
        } catch (e: Exception) {
            Logger.e("$TAG:Failed to import font file:$e")
            ImportResult(false, errorMessage = e.message)
        }
    }

    suspend fun importFontDirectory(treeUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val fontFiles = scanFontFilesInDirectory(treeUri)
            if (fontFiles.isEmpty()) {
                return@withContext ImportResult(
                    false, errorMessage = "No font files found"
                )
            }

            val fileInfos = fontFiles.mapNotNull { (uri, name) ->
                if (FontFamilyAnalyzer.isFontFile(name)) {
                    Triple(FontFamilyAnalyzer.analyze(name), uri, name)
                } else null
            }

            if (fileInfos.isEmpty()) {
                return@withContext ImportResult(
                    false, errorMessage = "No valid font files found"
                )
            }

            val grouped = fileInfos.groupBy { it.first.familyName }
            val now = System.currentTimeMillis()
            val importedFamilies = mutableListOf<String>()

            for ((familyName, entries) in grouped) {
                val fontId = generateFontId(familyName)
                val dirSuffix = generateDirSuffix(familyName)
                val fontDir = File(File(context.filesDir, "fonts"), "$IMPORT_PREFIX$dirSuffix")

                try {
                    fontDir.mkdirs()

                    val fileEntities = mutableListOf<FontFileEntity>()
                    var allCopied = true

                    for ((info, fileUri, originalName) in entries) {
                        val extension = originalName.substringAfterLast('.', "ttf")
                        val localFileName = "${info.variant}.$extension"
                        val targetFile = File(fontDir, localFileName)

                        context.contentResolver.openInputStream(fileUri)?.use { input ->
                            targetFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        if (!targetFile.exists() || targetFile.length() == 0L) {
                            allCopied = false
                            break
                        }

                        fileEntities.add(
                            buildFileEntity(fontId, info, localFileName, targetFile, now)
                        )
                    }

                    if (!allCopied) {
                        fontDir.deleteRecursively()
                        continue
                    }

                    val existingEntity = fontRepository.getFontById(fontId)
                    if (existingEntity == null) {
                        val entity = FontEntity(
                            id = fontId,
                            displayName = familyName,
                            category = "imported",
                            language = "unknown",
                            dirName = "$IMPORT_PREFIX$dirSuffix",
                            localDir = fontDir.absolutePath,
                            downloadedAt = now,
                            createdAt = now,
                            source = "import"
                        )
                        fontRepository.saveDownloadedFont(entity, fileEntities)
                    } else {
                        fontRepository.insertFontFiles(fileEntities)
                    }
                    importedFamilies.add(familyName)
                } catch (e: Exception) {
                    Logger.e("$TAG:Failed to import font family: $familyName,$e")
                    if (fontDir.exists() && fontDir.listFiles()?.isEmpty() == true) {
                        fontDir.deleteRecursively()
                    }
                }
            }

            if (importedFamilies.isEmpty()) {
                ImportResult(false, errorMessage = "Failed to import any font families")
            } else {
                ImportResult(success = true, importedFamilies = importedFamilies)
            }
        } catch (e: Exception) {
            Logger.e("$TAG:Failed to import font directory,$e")
            ImportResult(false, errorMessage = e.message)
        }
    }

    private suspend fun saveFontToDatabase(
        fontId: String,
        familyName: String,
        fontDir: File,
        fileInfo: FontFamilyAnalyzer.FontFileInfo,
        localFileName: String,
        targetFile: File
    ) {
        val now = System.currentTimeMillis()
        val existingEntity = fontRepository.getFontById(fontId)

        if (existingEntity == null) {
            val entity = FontEntity(
                id = fontId,
                displayName = familyName,
                category = "imported",
                language = "unknown",
                dirName = "$IMPORT_PREFIX${generateDirSuffix(familyName)}",
                localDir = fontDir.absolutePath,
                downloadedAt = now,
                createdAt = now,
                source = "import"
            )
            val fileEntity = buildFileEntity(fontId, fileInfo, localFileName, targetFile, now)
            fontRepository.saveDownloadedFont(entity, listOf(fileEntity))
        } else {
            val fileEntity = buildFileEntity(fontId, fileInfo, localFileName, targetFile, now)
            fontRepository.insertFontFiles(listOf(fileEntity))
        }
    }

    private fun buildFileEntity(
        fontId: String,
        info: FontFamilyAnalyzer.FontFileInfo,
        localFileName: String,
        targetFile: File,
        now: Long
    ): FontFileEntity {
        val fileHash = info.originalFileName.hashCode().toString(16)
        return FontFileEntity(
            id = "${fontId}_${info.variant}_$fileHash",
            fontId = fontId,
            variant = info.variant,
            name = info.variant.replaceFirstChar { it.uppercase() },
            url = "",
            fileName = info.originalFileName,
            localFileName = localFileName,
            localPath = targetFile.absolutePath,
            downloadedAt = now
        )
    }

    private fun scanFontFilesInDirectory(treeUri: Uri): List<Pair<Uri, String>> {
        val results = mutableListOf<Pair<Uri, String>>()
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
            scanDirectoryRecursive(treeUri, treeDocId, results)
        } catch (e: Exception) {
            Logger.e("$TAG:Failed to scan font directory:$e")
        }
        return results
    }

    private fun scanDirectoryRecursive(
        treeUri: Uri,
        parentDocId: String,
        results: MutableList<Pair<Uri, String>>
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn)
                val mimeType = cursor.getString(mimeColumn)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    scanDirectoryRecursive(treeUri, docId, results)
                } else if (FontFamilyAnalyzer.isFontFile(name)) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    results.add(docUri to name)
                }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("$TAG:Failed to get filename from URI: $uri, $e")
        }
        return name?.takeIf { it.isNotEmpty() }
    }

    private fun generateFontId(familyName: String): String {
        val sanitized = familyName.lowercase().replace("[^\\p{L}\\p{N}]".toRegex(), "_")
        val hash = familyName.hashCode().toString(16)
        return "${IMPORT_PREFIX}${sanitized}_$hash"
    }

    private fun generateDirSuffix(familyName: String): String {
        val sanitized = sanitize(familyName)
        val hash = familyName.hashCode().toString(16).take(8)
        return "${sanitized}_$hash"
    }

    private fun sanitize(name: String): String {
        return name.replace("[^\\p{L}\\p{N}_-]".toRegex(), "_")
    }
}
