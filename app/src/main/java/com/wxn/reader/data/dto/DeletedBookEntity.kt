package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deleted_books",
    indices = [Index(value = ["scanDirectoryUri"])]
)
data class DeletedBookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: String,
    val scanDirectoryUri: String,
    val fileName: String,
    val deletedAt: Long = System.currentTimeMillis()
)
