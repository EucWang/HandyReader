package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_activities")
data class ReadingActiveEntity (
    @PrimaryKey val date: Long, // Date in milliseconds
    val readingTime: Long, // Reading time in milliseconds
)