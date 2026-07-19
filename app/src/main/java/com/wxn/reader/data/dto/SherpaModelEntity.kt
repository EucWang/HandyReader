package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "sherpa_models",
    indices = [
        Index(value = ["locale"], name = "idx_sherpa_model_locale"),
        Index(value = ["type"], name = "idx_sherpa_model_type")
    ]
)
data class SherpaModelEntity(
    @PrimaryKey
    val name: String,                        // 模型名称 (主键)

    val url: String,                         // 模型下载 URL
    val type: String,                        // 模型类型: "matcha-icefall", "vits-piper" 等
    val locale: String,                      // 语言环境: "en_US", "zh-CN" 等
    val size: String,                        // 文件大小: "67.83MB"
    val speakersNum: Int,                    // Speaker 数量

    val processSpeed: Float,
    val quality: Float,

    // 基础依赖的数据
    val baseDatas: String,                   //

    val localPath: String? = null,           // 本地存储路径

    val downloadedAt: Long? = null,          // 下载时间戳

    val createdAt: Long = System.currentTimeMillis(),  // 创建时间

    val license: String? = null,    //备用字段, 后续回补充

    val licenseUrl: String? = null,    //备用字段, 后续回补充

    val remark: String? = null,    //备用字段
)