package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fonts")
data class FontEntity(
    @PrimaryKey
    val id: String,
    val displayName: String, //界面上显示的名称
    val category: String,  //分类： 手写体handwriting， 衬线serif，非衬线sans_serif， 等宽monospace
    val language: String, //语言： latin（en,fr, de, es, pt, it）, zh, ja, ar
    val dirName: String, //本地存储时的目录名
    val localDir: String? = null,  //本地存储时的路径
    val downloadedAt: Long? = null,  //下载时间
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "download"
)
