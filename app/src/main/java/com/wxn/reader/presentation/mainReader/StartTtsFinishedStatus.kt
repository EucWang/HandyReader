package com.wxn.reader.presentation.mainReader

/**
 *   -1: 章节没有内容;
 *  -2: 设置播放数据失败;
 *  -3: 播放被停止了,
 *  -4: 引擎初始化失败
 *  -5: 引擎需要加载模型
 *  1: 引擎初始化成功
 */
enum class StartTtsFinishedStatus(val value: Int) {
    NoChapterData(-1),
    SetDataFail(-2),
    PlayStopFail(-3),
    EngineInitFail(-4),
    EngineFailByNeedModel(-5),
    EngineInitSuccess(1)
}