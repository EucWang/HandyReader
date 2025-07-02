package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["bookId"])]
)
data class BookAnnotationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val locator: String,
    val color: String,
    val note: String?,
    val type: AnnotationType
)

enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE;

    override fun toString(): String {
        return when(this) {
            HIGHLIGHT -> {
                "highlight"
            }
            UNDERLINE -> {
                "underline"
            }
        }
    }
}

