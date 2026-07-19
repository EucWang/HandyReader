package com.wxn.reader.presentation.mainReader.models

import kotlinx.coroutines.Job

/***
 * 垂直滚动模式下的预加载状态
 */
data class PreloadState(val job: Job, val targetIndex: Int)