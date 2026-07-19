package net.gotev.speech.engine

enum class PlayErrorCode(val value: Int) {
    PlayErrorShuttingDown(-1), //播放时,已经被关停
    PlayErrorStop(-2),      //播放时, 已经停止播放
    PlayErrorTtsIsNull(-3), //播放时, 引擎为空
    PlayErrorSpeakerIdInvalid(-4), //播放时,说话人id无效
    PlayErrorSpeedInvalid(-5), //播放时,播放速度无效

    PlayErrorAudioTrackInvalid(-6), //播放时,流媒体播放器无效
    PlayerErrorAudioTrackPrepareFail(-7), //播放时,播放器预备失败
    PlayErrorAudioGenerateFail(-8), //播放时,音频生成失败
}