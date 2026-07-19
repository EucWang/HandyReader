package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["uri"]),
        Index(value = ["deleted", "importStatus"]),
        Index(value = ["documentId"], unique = true),
        Index(value = ["contentHash"]),
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,           //+
    val uri: String,            //+ filePath
    val fileType: String,

    val title: String,          //+
    val authors: String,        //+    ->author
    val description: String?,   //+

    val publishDate: String?, // New: Publication date 出版日期
    val publisher: String?, // New: Publisher  出版商
    val language: String?, // New: Primary language 语言
    val numberOfPages: Int?, // New: Total number of pages 总页数
    val wordCount: Long,   // 总字数

    val subjects: String?,      //+ New: Categories or genres  -> category 分类

    val coverPath: String?,     //+ image 封面图

    val locator: String, //阅读位置

    val progression: Float = 0f, //+ reading progression in % ->progress 当前阅读进度

    val lastOpened: Long? = null, //+  timestamp of the last time the book was opened 最后一次打开的时间戳

    val deleted: Boolean = false, // flag to mark the book as deleted 是否删除

    val rating: Float = 0f, // rating of the book  标星
    val isFavorite: Boolean = false, // flag to mark the book as favorite 是否最喜欢
    val favoriteDate: Long? = null, // timestamp of when the book was favorited, 用于首页"最近收藏"排序

    val readingStatus: ReadingStatus? = ReadingStatus.NOT_STARTED, // reading status of the book
    val readingTime: Long = 0, // total time spent reading the book in milliseconds
    val startReadingDate: Long? = null, // timestamp of when the user started reading the book
    val endReadingDate: Long? = null, // timestamp of when the user finished reading the book
    val review: String? = null,
    val duration: Long? = null, // Total duration of the audiobook in milliseconds
    val narrator: String? = null, // Name of the audiobook narrator

    var scrollIndex: Int = 0,           // + 当前阅读的章节索引
    var scrollOffset: Int = 0,          // +  当前阅读的章节中的字符偏移量

    var cachedDir: String = "",         //+ 缓存目录, 当缓存目录创建成功之后，会设置这个值
    var crc: Int = 0,                   //+  文件校验码
    var importStatus: Int = 0,          //+ 导入状态: 0=正常, -1=导入失败
    val source: String = "scan",        //+ 书籍来源: "scan" | "opds" | "import" | "external" | "external_import"
    val documentId: String? = null,     //+ SAF document ID, 用于跨URI格式去重

    // ===== ★ v12 TXT 统一字节偏移方案（plan-txt-unify-byte-offset.md §4.1）=====
    /**
     * TXT 专用：字符编码名（带端序，如 UTF-8 / UTF-16LE / GBK）。
     *
     * 仅 TXT 格式使用，其他格式（EPUB/MOBI/PDF/...）为 null。
     * 重开书时 [com.wxn.bookparser.parser.txt.TxtTextParser.resolveCharsetName] 从此字段读取编码，
     * 避免每次打开都重新探测。老用户升级后此字段为 null，首次打开时现场探测并回填。
     */
    val txtCharset: String? = null,

    // ===== ★ 同步方案 v2.6 §2.7.3 一期新增(contentHash + 9 HLC)=====
    /** SHA-256 全文件指纹(导入时算;老用户升级后台静默补算)。 */
    val contentHash: String? = null,
    /** 部分文件 MD5(大文件流式预扫用,一期可空)。 */
    val partialMd5: String? = null,
    // meta 档(标题/作者/封面/语言等元数据变更)
    val metaHlcL: Long = 0L,
    val metaHlcC: Int = 0,
    val metaHlcDevice: String = "",
    // user 档(评分/收藏/书评/阅读状态)
    val userHlcL: Long = 0L,
    val userHlcC: Int = 0,
    val userHlcDevice: String = "",
    // reading 档(进度/locator/scrollIndex)
    val syncHlcL: Long = 0L,
    val syncHlcC: Int = 0,
    val syncHlcDevice: String = "",
) {
    @androidx.room.Ignore
    fun effectiveSource(): String = if (source.isEmpty()) "scan" else source
}

enum class FileType {
    EPUB,
    PDF,
    AUDIOBOOK, //mp3
    TXT,
    FB2,
    HTML,
    MD,
    MOBI,
    AZW3,
    UNKNOWN;

    companion object {
        private val STRING_TO_TYPE: Map<String, FileType> by lazy {
            FileType.entries.flatMap { ft ->
                ft.storageValues().map { v -> v to ft }
            }.toMap()
        }

        fun stringToFileType(type: String): FileType =
            STRING_TO_TYPE[type.lowercase().trim()] ?: FileType.UNKNOWN
    }

    /**
     * 该类型的书在 DB fileType 列可能出现的所有值（均为小写）。
     * 依赖存储层统一写入小写扩展名；新增存储路径必须保证小写。
     * 过滤查询时展开为该列表进行 IN 匹配。
     */
    fun storageValues(): List<String> =
        when (this) {
            EPUB -> listOf("epub")
            PDF -> listOf("pdf")
            AUDIOBOOK -> listOf("mp3", "m4a", "m4b", "aac")
            TXT -> listOf("txt")
            FB2 -> listOf("fb2")
            HTML -> listOf("html", "htm")
            MD -> listOf("md")
            MOBI -> listOf("mobi")
            AZW3 -> listOf("azw3")
            UNKNOWN -> emptyList()
        }

    fun typeName(): String =
        when (this) {
            EPUB -> "epub"
            PDF -> "pdf"
            AUDIOBOOK -> "AUDIOBOOK"
            TXT -> "txt"
            FB2 -> "fb2"
            HTML -> "html"
            MD -> "md"
            MOBI -> "mobi"
            AZW3 -> "azw3"
            UNKNOWN -> ""
        }

    fun showName() : String = when(this) {
        EPUB -> "EPUB"
        PDF -> "PDF"
        AUDIOBOOK -> "AUDIO"
        TXT -> "TXT"
        FB2 -> "FB2"
        HTML -> "HTML"
        MD -> "MD"
        MOBI -> "MOBI"
        AZW3 -> "AZW3"
        UNKNOWN -> ""
    }
}


enum class ReadingStatus(val value: Int) {
    NOT_STARTED(0),    // 0
    IN_PROGRESS(1),    // 1
    FINISHED(2)        //2
    ;

    companion object {

        fun intToReadStatus(status: Int?) = when (status) {
            0 -> ReadingStatus.NOT_STARTED
            1 -> ReadingStatus.IN_PROGRESS
            2 -> ReadingStatus.FINISHED
            else -> ReadingStatus.NOT_STARTED
        }
    }
}