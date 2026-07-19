package com.wxn.mobi.inative

import android.content.Context
import com.wxn.base.bean.BookChapter
import com.wxn.base.util.Logger
import com.wxn.mobi.data.model.CountPair
import com.wxn.mobi.data.model.MetaInfo
import com.wxn.mobi.data.model.ParagraphData
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile

object NativeLib {

    @Volatile
    private var nativeLoaded = false

    private val metaInfoJson = Json { ignoreUnknownKeys = true }

    init {
        try {
            System.loadLibrary("appmobi")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Logger.e("NativeLib:Failed to load native library: appmobi:$e")
        }
    }

    fun tryLoad(): Boolean {
        if (nativeLoaded) return true
        return try {
            System.loadLibrary("appmobi")
            nativeLoaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            Logger.e("NativeLib:Retry: Failed to load native library: appmobi:$e")
            false
        }
    }

    // Private external implementations (renamed to match C++ JNI names)
    private external fun searchFilesNative(root: String, patterns: Array<String>): Array<String>
    private external fun loadMobiNative(context: Context, path: String): String?
    private external fun loadEpubNative(context: Context, path: String): String?
    private external fun loadFb2Native(context: Context, path: String): String?
    private external fun getChaptersNative(context: Context, bookId: Long, path: String, type: Int): Array<BookChapter>?
    private external fun getChapterNative(context: Context, path: String, chapter: BookChapter, type: Int): Array<ParagraphData>?
    private external fun getWordCountNative(bookId: Long, path: String, type: Int): List<CountPair>
    private external fun closeBookNative(bookId: Long, path: String, type: Int)

    private external fun nativeMatchChapterTitleNative(title: String): Int

    // Public wrapper methods with null/empty fallback when native library not loaded
    fun searchFiles(root: String, patterns: Array<String>): Array<String> {
        return if (nativeLoaded) searchFilesNative(root, patterns) else emptyArray()
    }

    fun loadMobi(context: Context, path: String): MetaInfo? {
        if (!nativeLoaded) return null
        return loadMobiNative(context, path)?.let { json ->
            try { metaInfoJson.decodeFromString<MetaInfo>(json) } catch (e: Exception) { null }
        }
    }

    fun loadEpub(context: Context, path: String): MetaInfo? {
        if (!nativeLoaded) return null
        return loadEpubNative(context, path)?.let { json ->
            try { metaInfoJson.decodeFromString<MetaInfo>(json) } catch (e: Exception) { null }
        }
    }

    fun loadFb2(context: Context, path: String): MetaInfo? {
        if (!nativeLoaded) return null
        return loadFb2Native(context, path)?.let { json ->
            try {
                metaInfoJson.decodeFromString<MetaInfo>(json)
            } catch (e: Exception) {
                null
            }
        }
    }

    fun getChapters(context: Context, bookId: Long, path: String, type: Int): Array<BookChapter>? {
        return if (nativeLoaded) getChaptersNative(context, bookId, path, type) else null
    }

    fun getChapter(context: Context, path: String, chapter: BookChapter, type: Int): Array<ParagraphData>? {
        return if (nativeLoaded) getChapterNative(context, path, chapter, type) else null
    }

    fun getWordCount(bookId: Long, path: String, type: Int): List<CountPair> {
        return if (nativeLoaded) getWordCountNative(bookId, path, type) else emptyList()
    }

    fun closeBook(bookId: Long, path: String, type: Int) {
        if (nativeLoaded) closeBookNative(bookId, path, type)
    }

    /**
     * 章节标题匹配（通过 native chapter_matcher 实现）
     * @return 0=不匹配 / 1=匹配（章/节等） / 2=匹配且含"卷"
     */
    fun matchChapterTitle(title: String): Int {
        return if (nativeLoaded) nativeMatchChapterTitleNative(title) else 0
    }
}
