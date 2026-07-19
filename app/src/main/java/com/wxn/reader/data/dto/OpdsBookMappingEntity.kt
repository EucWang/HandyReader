package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "opds_book_mapping",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["remoteUrl", "catalogId"], unique = true),
        Index(value = ["bookId"])
    ]
)
data class OpdsBookMappingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val remoteUrl: String,
    val catalogId: Long,
    val bookId: Long
)
