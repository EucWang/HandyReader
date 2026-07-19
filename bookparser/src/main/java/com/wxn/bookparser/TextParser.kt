package com.wxn.bookparser

import com.wxn.base.bean.BookChapter
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.base.bean.ReaderText


/****
 * 接口，提供对底层不同格式书籍的文件进行解析的统一接口
 */
interface TextParser {

    /****
     * 解析文件，得到对应文件的内容的列表
     * ReaderText 是一个展示数据的一个封装
     */
//    suspend fun parse(bookId: Long, cachedFile: CachedFile): List<ReaderText>

    /***
     * 解析得到章节列表
     */
    suspend fun parseChapterInfo(bookId: Long, cachedFile: CachedFile): List<BookChapter>

    /***
     * 解析得到给定章节数据
     */
    suspend fun parsedChapterData(bookId: Long, cachedFile: CachedFile, chapter: BookChapter) : List<ReaderText>

    suspend fun getWordCount(bookId:Long, cachedFile: CachedFile): List<Triple<Int, Int, Int>>

    suspend fun close(bookId:Long, cachedFile: CachedFile)

    /**
     * 是否需要因格式迁移而重扫章节。
     *
     * 默认 false（非 TXT 格式无此概念）。`TxtTextParser` 覆写为检测老格式 `chapterUrl`
     * （非 `b:` 前缀），由 `MainReadViewModel` 打开书守卫调用触发一次性重扫升级。
     *
     * 详见 `docs/plans/plan-txt-unify-byte-offset.md` §3.3.4。
     */
    fun needsRescanForMigration(chapters: List<BookChapter>): Boolean = false

    /**
     * 返回最近一次扫描得到的 charsetName（若 memo 命中且 bookId 匹配），否则 null。
     *
     * 默认 null（非 TXT 格式无此概念）。仅 [com.wxn.bookparser.parser.txt.TxtTextParser] override。
     *
     * 由 `MainReadViewModel` 在 `replaceChaptersByBookIdUseCase` 后调用，把扫描时探测到的
     * charsetName 回填到 `BookEntity.txtCharset`，下次打开时 `TxtTextParser.resolveCharsetName`
     * 直接命中（O(1)），无需重新探测文件头（详见 `plan-txt-unify-byte-offset.md` §3.3.3）。
     *
     * **设计原因**（为什么放进接口而不是 downcast 到 TxtTextParser）：
     * Hilt 注入的实际类型是 [com.wxn.bookparser.impl.TextParserImpl]，downcast 到 TxtTextParser
     * 会永远失败（详见 `docs/reviews/2026-07-18-txt-chapter-scanner-review.md` 遗漏 #2）。
     */
    fun lastScanCharsetName(bookId: Long): String? = null
}