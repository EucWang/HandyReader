package com.wxn.bookparser.parser.audio

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.extension
import com.wxn.bookparser.FileParser
import com.wxn.base.bean.Book
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.util.FileUtil
import com.wxn.bookparser.util.getCoverPath
import javax.inject.Inject

class AudioFileParser @Inject constructor(val context: Context) : FileParser {
    override suspend fun parse(cachedFile: CachedFile): Book? {
        val title = cachedFile.name.substringBeforeLast(".").trim()
        val uri = cachedFile.uri
        val format = cachedFile.extension
        Log.d("AudioFileParser", "title=$title, uri=$uri, format=$format")

        return innerParse(context, title, uri, format)
    }

    override suspend fun parse(file: DocumentFile): Book? {
        val title = file.baseName.orEmpty()
        val uri = file.uri
        val format = file.extension
        Log.d("AudioFileParser", "title=$title, uri=$uri, format=$format")
        return innerParse(context, title, uri, format)
    }

    private suspend fun innerParse(context: Context, titleName: String, uri: Uri, format: String): Book? {
//        val uri = documentFile.uri
        val mediaMetadataRetriever = MediaMetadataRetriever()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                mediaMetadataRetriever.setDataSource(descriptor.fileDescriptor)

                val title =
                    mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: titleName // documentFile.name ?: "Unknown"
                val artist =
                    mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album =
                    mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val duration =
                    mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()

                // Extract cover art
                var coverPath = getCoverPath(context, uri.toString().hashCode().toString())
                mediaMetadataRetriever.embeddedPicture?.let { bmpBytes ->
                    if (!FileUtil.saveBitmapToFile(
                            context,
                            BitmapFactory.decodeByteArray(bmpBytes, 0, bmpBytes.size),
                            getCoverPath(context, uri.toString().hashCode().toString())
                        )
                    ) {
                        coverPath = ""
                    }
                }
                Book(
                    filePath = uri.toString(),
                    fileType = format, //FileType.AUDIOBOOK.typeName(),
                    title = title,
                    author = artist ?: "",
                    description = album,
                    publishDate = null,
                    publisher = null,
                    language = null,
                    numberOfPages = null,
                    category = "",
                    coverImage = coverPath,
                    locator = "",
                    duration = duration,
                    narrator = artist,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    lastOpened = 0,
                )
            } ?: throw IllegalStateException("Unable to open audio file")
        } catch (e: Exception) {
            Book(
                filePath = uri.toString(),
                fileType = format, // FileType.AUDIOBOOK.typeName(),
                title = titleName, // documentFile.name ?: "Unknown",
                author = "",
                description = null,
                publishDate = null,
                publisher = null,
                language = null,
                numberOfPages = null,
                category = "",
                coverImage = "",
                locator = "",
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                lastOpened = 0,
            )
        } finally {
            mediaMetadataRetriever.release()
        }
    }
}