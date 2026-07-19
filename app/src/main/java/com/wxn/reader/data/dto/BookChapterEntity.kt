package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class BookChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id : Long = 0,

    val bookId: Long,
    val chapterIndex: Int,
    var chapterName: String,
    val createTimeValue: Long,
    val updateDate: String,
    val updateTimeValue: Long,
    val chapterUrl: String?,

    val srcName: String?,

    val chaptersSize: Int,

    val chapterId : String = "",
    val parentChapterId: String = "",

    val wordCount: Long = 0,
    val picCount: Long = 0,
    val chapterProgress: Float = 0f,

    /*** 章节类型，0=正常，1=大章节自动切分 */
    val type: Int = 0,
    /*** 自动切分章在切分后的顺序 */
    val splitSeq: Int = -1
) {
}