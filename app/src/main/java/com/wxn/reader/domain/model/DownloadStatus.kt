package com.wxn.reader.domain.model

enum class DownloadStatus {

    INIT,

    COMPLETED,
    FAILED,
    CANCELLED,

    DELETED //已删除
}