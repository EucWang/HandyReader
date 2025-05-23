package com.wxn.reader.domain.use_case.chapters

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.spreada.utils.chinese.ZHConverter
import com.wxn.base.bean.Book
import com.wxn.base.bean.BookChapter
import com.wxn.base.bean.ReaderText
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.source.local.AppPreferencesUtil

object BookHelper {

    /***
     * 从文件中解析得到章节信息
     */
    suspend fun getChapters(context: Context, bookId: Long, bookUri: String?, textParser: TextParser): List<BookChapter> {
        bookUri?.let { uri ->
            val cachedFile = CachedFileCompat.fromUri(context, uri.toUri())
            val chapters = textParser.parseChapterInfo(cachedFile)
            chapters.forEach { chapter ->
                chapter.bookId = bookId
            }
            return chapters
        }
        return emptyList()
    }

    /***
     * 将不同的章节缓存到不同的文件中
     */
//    @Deprecated("optimazed")
//    suspend fun cacheBookChapter(context: Context, bookId: Long, bookUri: String?, textParser: TextParser): List<BookChapter> {
//        val start = System.currentTimeMillis()
//        var chapters = arrayListOf<BookChapter>()
//        Logger.d("MainReadViewModel::cacheBookChapter::bookId=$bookId,bookUri=$bookUri, ")
//        bookUri?.let { uri ->
//            val cachedFile = CachedFileCompat.fromUri(context, uri.toUri())
//            val texts = textParser.parse(bookId, cachedFile)
//            Logger.d("MainReadViewModel::texts.size=${texts.size}")
//
//            var curChapter: BookChapter? = null
//            var chapterIndex = 0
//            var chapterPath = ""
//            var cachedTxtWriter: BufferedWriter? = null
//
//            try {
//                for (text in texts) {
//                    when (text) {
//                        is ReaderText.Chapter -> {
//                            if (curChapter != null) {
//                                chapters.add(curChapter)
//                            }
//                            if (cachedTxtWriter != null) {
//                                cachedTxtWriter.flush()
//                                cachedTxtWriter.close()
//                                Logger.d("MainReadViewModel:write chapter: $chapterPath")
//                                cachedTxtWriter = null
//                            }
//                            //------------------------------
//                            curChapter = null
//                            curChapter = BookChapter(
//                                bookId = bookId,
//                                chapterIndex = chapterIndex++,
//                                chapterName = text.title,
//                                cachedName = text.id.toString()
//                            )
//                            var cachedName = text.id.toString()
//                            val file = PathUtil.getChapterFile(context, bookId, cachedName)
//                            chapterPath = file.absolutePath
//                            cachedTxtWriter = BufferedWriter(FileWriter(file))
//                            Logger.d("BookHelper::chapterPath=$chapterPath")
////                            cachedTxtWriter.write(cachedName)
////                            cachedTxtWriter.newLine()
//                        }
//
//                        is ReaderText.Text -> {
//                            cachedTxtWriter?.apply {
//                                write(text.line.toString())
//                                newLine()
//                            }
//                        }
//
//                        is ReaderText.Image -> {
////                            val resName = UUID.randomUUID().toString() + ".jpg"
////                            val width = text.imageBitmap.width
////                            val height = text.imageBitmap.height
////                            if (FileUtil.saveBitmapToFile(
////                                    context,
////                                    text.imageBitmap.asAndroidBitmap(),
////                                    PathUtil.getChapterResourcePath(context, bookId, resName).absolutePath
////                                )
////                            ) {
//
//                            cachedTxtWriter?.apply {
//                                write("<img src=\"${text.path}\" width=\"${text.width}\" height=\"${text.height}\" />")
//                                newLine()
//                            }
////                            }
//                        }
//
//                        is ReaderText.Separator -> {
//                            cachedTxtWriter?.apply {
//                                write("---")
//                                newLine()
//                            }
//                        }
//
//                        else -> {
//                        }
//                    }
//                }
//                if (curChapter != null) {
//                    chapters.add(curChapter)
//                }
//                if (cachedTxtWriter != null) {
//                    cachedTxtWriter.flush()
//                    cachedTxtWriter.close()
//                    cachedTxtWriter = null
//                }
//            } catch (ex: Exception) {
//                Logger.e(ex)
//                chapters.clear()
//            } finally {
//                try {
//                    cachedTxtWriter?.close()
//                } catch (ex: Exception) {
//                }
//            }
//        }
//        val spendTime = System.currentTimeMillis() - start
//        Logger.d("MainReadViewModel::chapters.size=${chapters.size}, spendTime=${spendTime}")
//        return chapters
//    }

    /***
     * 从缓存文件中加载章节内容
     */
//    @Deprecated("optimazed")
//    fun loadChpaterContent0(context: Context, bookId: Long, chapter: BookChapter): String? {
//        val chapterCachedName = chapter.cachedName ?: return null
//        PathUtil.getChapterFile(context, bookId, chapterCachedName).let { file ->
//            if (file.exists()) {
//                return file.readText()
//            }
//        }
//        return null
//    }

    suspend fun loadChapterContent(context: Context, book: Book, chapter: BookChapter, textparser: TextParser): List<ReaderText> {
        val encodedUri =  book.filePath.toUri()
        val cachedFile = CachedFileCompat.fromUri(context, encodedUri)
        val bookId = book.id
        val chapterIndex = chapter.chapterIndex
        return textparser.parsedChapterData(bookId, cachedFile, chapterIndex)
    }

    suspend fun disposeContent(
        appPreferencesUtil: AppPreferencesUtil,
        chapter: BookChapter,
        contents: List<ReaderText>
    ): List<ReaderText> {
        val chineseConverterType = appPreferencesUtil.chineseConverterType()
        //得到简繁体对应的章节名称
        chapter.chapterName = when (chineseConverterType) {
            1 -> ZHConverter.getInstance(ZHConverter.SIMPLIFIED).convert(chapter.chapterName)
            2 -> ZHConverter.getInstance(ZHConverter.TRADITIONAL).convert(chapter.chapterName)
            else -> chapter.chapterName
        }
        var title1: String = chapter.chapterName
        var content1: List<ReaderText> = contents
        try {
            when (chineseConverterType) {
                1 -> {
                    ZHConverter.getInstance(ZHConverter.SIMPLIFIED)
                }

                2 -> {
                    ZHConverter.getInstance(ZHConverter.TRADITIONAL)
                }

                else -> {
                    null
                }
            }?.let { converter ->
                title1 = converter.convert(title1)
                content1.forEach { content ->
                    if (content is ReaderText.Text) {
                        content.line = converter.convert(content.line)
                    } else if (content is ReaderText.Chapter) {
                        content.title = converter.convert(content.title)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e)
        }
        content1.forEach { content ->
            if (content is ReaderText.Text) {
                val line = content.line.replace("^[\\n\\s\\r]+".toRegex(), "")
                content.line = "${ChapterProvider.paragraphIndent}$line"
            }
        }
        return content1
    }
}