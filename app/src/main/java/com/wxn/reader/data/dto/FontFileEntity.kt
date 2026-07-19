package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "font_files",
    foreignKeys = [
        ForeignKey(
            entity = FontEntity::class,
            parentColumns = ["id"],
            childColumns = ["fontId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["fontId"], name = "idx_font_files_fontId")
    ]
)
data class FontFileEntity(
    @PrimaryKey
    val id: String,
    val fontId: String,
    val variant: String,
    val name: String,
    val url: String,
    val fileName: String,
    val localFileName: String,
    val localPath: String? = null,
    val downloadedAt: Long? = null
)
