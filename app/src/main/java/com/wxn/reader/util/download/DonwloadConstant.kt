package com.wxn.reader.util.download


const val MAX_CONCURRENT_DOWNLOADS = 2  //同时下载的任务数

const val MIN_FREE_SPACE = 50L * 1024 * 1024       // 50MB最小保留
const val BUFFER_SMALL = 32 * 1024                // 32KB缓冲区（小文件）
const val BUFFER_LARGE = 128 * 1024                // 128KB缓冲区（大文件>10MB）
const val SPACE_CHECK_INTERVAL = 20L * 1024 * 1024 // 每20MB检查磁盘
//const val PROGRESS_REPORT_INTERVAL = 0.05f        // 每5%回调进度
const val RETRY_COUNT = 3                         // 重试次数
const val RETRY_DELAY = 1000L                     // 初始重试延迟1秒

const val PROGRESS_INTERVAL_SMALL = 0.05f    // 5%（小/中文件）
const val PROGRESS_INTERVAL_LARGE = 0.01f    // 1%（大文件）
const val INITIAL_RETRY_DELAY = 30_000L      // 30秒
const val LARGE_FILE_THRESHOLD = 20 * 1024 * 1024  // 20MB

const val UPDATE_METADATA_SIZE = 5 * 1024 * 1024L