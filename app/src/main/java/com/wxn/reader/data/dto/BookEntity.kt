package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
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
)

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
        fun stringToFileType(type: String): FileType =
            when (type.lowercase()) {
                "epub" -> FileType.EPUB
                "pdf" -> FileType.PDF
                in listOf("mp3", "m4a", "m4b", "aac","AUDIOBOOK") -> AUDIOBOOK
                "txt" -> FileType.TXT
                "fb2" -> FileType.FB2
                "html", "htm" -> FileType.HTML
                "md" -> FileType.MD
                "mobi" -> FileType.MOBI
                "azw3" -> FileType.AZW3
                else -> FileType.UNKNOWN
            }
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