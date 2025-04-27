package com.wxn.reader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wxn.bookparser.parser.epub.EpubFileParser

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,           //+
    val uri: String,            //+ filePath
    val fileType: FileType,

    val title: String,          //+
    val authors: String,        //+    ->author
    val description: String?,   //+

    val publishDate: String?, // New: Publication date 出版日期
    val publisher: String?, // New: Publisher  出版商
    val language: String?, // New: Primary language 语言
    val numberOfPages: Int?, // New: Total number of pages 总页数

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

    var scrollIndex: Int = 0,  //+
    var scrollOffset : Int = 0 //+
)

enum class FileType {
    EPUB,
    PDF,
    AUDIOBOOK, //mp3
    TXT,
    FB2,
    HTML,
    MD,
    UNKNOWN
}

fun stringToFileType(type:String): FileType =
    when(type.lowercase()) {
        "epub" -> FileType.EPUB
        "pdf" -> FileType.PDF
        "mp3" -> FileType.AUDIOBOOK
        "txt" -> FileType.TXT
        "fb2" -> FileType.FB2
        "html", "htm" -> FileType.HTML
        "md" -> FileType.MD
        else -> FileType.UNKNOWN
    }



enum class ReadingStatus(val value:Int) {
    NOT_STARTED(0),    // 0
    IN_PROGRESS(1),    // 1
    FINISHED(2)        //2
}

fun intToReadStatus(status:Int) = when(status) {
    0 -> ReadingStatus.NOT_STARTED
    1 -> ReadingStatus.IN_PROGRESS
    2 -> ReadingStatus.FINISHED
    else -> ReadingStatus.NOT_STARTED
}