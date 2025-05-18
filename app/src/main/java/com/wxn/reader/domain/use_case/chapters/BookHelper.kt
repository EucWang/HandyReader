package com.wxn.reader.domain.use_case.chapters

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.net.toUri
import com.spreada.utils.chinese.ZHConverter
import com.wxn.base.bean.BookChapter
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.bookparser.domain.reader.ReaderText
import com.wxn.bookparser.util.FileUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.source.local.AppPreferencesUtil
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.UUID

object BookHelper {

    /***
     * 将不同的章节缓存到不同的文件中
     */
    suspend fun cacheBookChapter(context: Context, bookId: Long, bookUri: String?, textParser: TextParser): List<BookChapter> {
        val start = System.currentTimeMillis()
        var chapters = arrayListOf<BookChapter>()
        Logger.d("MainReadViewModel::cacheBookChapter::bookId=$bookId,bookUri=$bookUri, ")
        bookUri?.let { uri ->
            val cachedFile = CachedFileCompat.fromUri(context, uri.toUri())
            val texts = textParser.parse(cachedFile)
            Logger.d("MainReadViewModel::texts.size=${texts.size}")

            var curChapter: BookChapter? = null
            var chapterIndex = 0
            var chapterPath = ""
            var cachedTxtWriter: BufferedWriter? = null

            try {
                for (text in texts) {
                    when (text) {
                        is ReaderText.Chapter -> {
                            if (curChapter != null) {
                                chapters.add(curChapter)
                            }
                            if (cachedTxtWriter != null) {
                                cachedTxtWriter.flush()
                                cachedTxtWriter.close()
                                Logger.d("MainReadViewModel:write chapter: $chapterPath")
                                cachedTxtWriter = null
                            }
                            //------------------------------
                            curChapter = null
                            curChapter = BookChapter(
                                bookId = bookId,
                                chapterIndex = chapterIndex++,
                                chapterName = text.title,
                                cachedName = text.id.toString()
                            )
                            var cachedName = text.id.toString()
                            val file = ChapterProvider.getChapterFile(context, bookId, cachedName)
                            chapterPath = file.absolutePath
                            cachedTxtWriter = BufferedWriter(FileWriter(file))
                            Logger.d("BookHelper::chapterPath=$chapterPath")
//                            cachedTxtWriter.write(cachedName)
//                            cachedTxtWriter.newLine()
                        }

                        is ReaderText.Text -> {
                            cachedTxtWriter?.apply {
                                write(text.line.toString())
                                newLine()
                            }
                        }

                        is ReaderText.Image -> {
                            val resName = UUID.randomUUID().toString() + ".jpg"
                            val width = text.imageBitmap.width
                            val height = text.imageBitmap.height
                            if (FileUtil.saveBitmapToFile(
                                    context,
                                    text.imageBitmap.asAndroidBitmap(),
                                    ChapterProvider.getChapterResourcePath(context, bookId, resName).absolutePath
                                )
                            ) {
                                cachedTxtWriter?.apply {
                                    write("<img src=\"${resName}\" width=\"$width\" height=\"$height\" />")
                                    newLine()
                                }
                            }
                        }

                        is ReaderText.Separator -> {
                            cachedTxtWriter?.apply {
                                write("---")
                                newLine()
                            }
                        }

                        else -> {
                        }
                    }
                }
                if (curChapter != null) {
                    chapters.add(curChapter)
                }
                if (cachedTxtWriter != null) {
                    cachedTxtWriter.flush()
                    cachedTxtWriter.close()
                    cachedTxtWriter = null
                }
            } catch (ex: Exception) {
                Logger.e(ex)
                chapters.clear()
            } finally {
                try {
                    cachedTxtWriter?.close()
                } catch (ex: Exception) {
                }
            }
        }
        val spendTime = System.currentTimeMillis() - start
        Logger.d("MainReadViewModel::chapters.size=${chapters.size}, spendTime=${spendTime}")
        return chapters
    }

    /***
     * 从缓存文件中加载章节内容
     */
    fun loadChpaterContent(context: Context, bookId: Long, chapter: BookChapter): String? {
        val chapterCachedName = chapter.chapterName ?: return null
        ChapterProvider.getChapterFile(context, bookId, chapterCachedName).let { file ->
            if (file.exists()) {
                return file.readText()
            }
        }
        return null
    }

    suspend fun disposeContent(
        appPreferencesUtil: AppPreferencesUtil,
        chapter: BookChapter,
        content: String
    ) : List<String> {
        val chineseConverterType = appPreferencesUtil.chineseConverterType()
        //得到简繁体对应的章节名称
        chapter.chapterName = when (chineseConverterType) {
            1 -> ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(chapter.chapterName)
            2 -> ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(chapter.chapterName)
            else -> chapter.chapterName
        }
        var title1: String = chapter.chapterName
        var content1: String = content
        try {
            when (chineseConverterType) {
                1 -> {
                    title1 = ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(title1)
                    content1 = ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(content1)
                }

                2 -> {
                    title1 = ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(title1)
                    content1 = ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(content1)
                }
            }
        } catch (e: Exception) {
            Logger.e(e)
        }
        val contents = arrayListOf<String>()
        content1.split("\n").forEachIndexed { index, content ->
            val str = content.replace("^[\\n\\s\\r]+".toRegex(), "")
            contents.add("${ChapterProvider.paragraphIndent}$str")
        }
        return contents
    }
}