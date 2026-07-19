package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "read_bgs")
data class ReadBgEntity(
    @PrimaryKey val id: String,
    val localPath: String,      // 本地文件路径
    val thumbnailPath: String,  // 缩略图路径
    val remoteImageUrl: String,  //远程大图路径
    val isDownloaded: Boolean,
    val version: Int,
    val downloadedAt: Long
)