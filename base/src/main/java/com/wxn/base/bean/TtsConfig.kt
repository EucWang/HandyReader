package com.wxn.base.bean

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

const val MODEL_TYPE_VITS_PIPER = "vits-piper"
const val MODEL_TYPE_MATCHA_ICEFALL = "matcha-icefall"
const val MODEL_TYPE_KOKORO = "kokoro"
const val MODEL_TYPE_KITTEN = "kitten"
data class EngineModelConfig(
    val engineModel: String,  //AI引擎使用到的模型名称
    val modelType: String,   //AI引擎使用的模型类型
//    val modelLocale: String,  //AI引擎使用的模型Locale

    val baseDatas: List<Triple<String, String, String>>,
    val speakerNum: Int,     //AI引擎使用的 speaker的数量

    val speaker: Int,    //AI引擎使用的 speaker 的名称
    val language: String,   //通用参数, 设置 语言/地区

    val modelDir: String = ""
)

/***
 * tts配置
 */
@Parcelize
@Immutable
data class TtsConfig(
    val engineType: Int,  //引擎类型

    val engineModel: String,  //AI引擎使用到的模型名称
    val modelType: String,   //AI引擎使用的模型类型
    val modelDir: String,
    val baseDatas: List<Triple<String, String, String>>, //fileId, url, localPath
    val speakerNum: Int,     //AI引擎使用的 speaker的数量

    val speaker: Int,    //AI引擎使用的 speaker 的名称

    val speed: Float,      //通用参数, 设置语速
    val pitch: Float,      //通用参数, 设置 语调
    val language: String,   //通用参数, 设置 语言/地区

) : Parcelable

