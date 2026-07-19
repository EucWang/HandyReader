package com.wxn.bookparser.parser.txt

/**
 * TXT 书籍元数据存储接口（DB 侧抽象）。
 *
 * 定义在 `bookparser` 模块以避免反向依赖 `app` 模块的 `BookRepository`。
 * 由 `app` 模块用 Hilt 提供实现并注入到 [TxtTextParser]。
 *
 * 详见 `docs/plans/plan-txt-unify-byte-offset.md` §3.3.3 / §3.5.1 / §3.5.2 / §10.1。
 */
interface TxtBookMetaStore {

    /**
     * 读取 [bookId] 对应的 TXT 字符编码名（带端序，如 `UTF-8` / `UTF-16LE` / `GBK`）。
     *
     * 返回 null 或空串表示尚未回填（老用户升级、非 TXT 格式、TXT 但未回填），
     * 调用方 [TxtTextParser.resolveCharsetName] 据此走现场探测 + 回填兜底。
     */
    suspend fun getCharset(bookId: Long): String?

    /**
     * 回填 TXT 字符编码名到 DB（[BookEntity.txtCharset] 列）。
     *
     * 写失败不应抛异常到上层（调用方用 runCatching 吞掉），下次打开再尝试回填。
     */
    suspend fun updateCharset(bookId: Long, charset: String)

    /**
     * 读取 [bookId] 全部章节的 wordCount 列表，按 chapterIndex 升序。
     *
     * 供 [TxtTextParser.getWordCount] 用，避免冷启动全文件重扫（§3.5.2）。
     * 类型为 Long（与 Room `BookChapterEntity.wordCount` 列一致），调用方按需转 Int。
     */
    suspend fun getChaptersWithWordCount(bookId: Long): List<Long>
}
