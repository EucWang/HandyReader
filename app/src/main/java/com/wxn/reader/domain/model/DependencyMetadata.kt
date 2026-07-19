package com.wxn.reader.domain.model

import kotlinx.serialization.Serializable

/***
 * TTS Model Dependency Model  Metadata Info
 */
@Serializable
data class DependencyMetadata(
    val url: String,  //依赖对应的url
    val fileName: String,  //依赖的文件名
    val targetPath: String,  //依赖的解压路径
    val downloadedAt: Long,  //依赖下载的时间戳
    val extractedAt: Long? = null,  //依赖解压缩的时间戳
    val status: String // "downloading", "extracting", "completed", "failed"
)