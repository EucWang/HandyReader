package com.wxn.reader.data.remote.api


object Constants {

    const val BASE_URL = com.wxn.reader.BuildConfig.FEEDBACK_API_URL

    const val DICTIONARY_BASE_URL = "https://dict.handyreader.top"

    const val AI_TRANSILATOR = "handyreader_ai_translator"

    const val BUILT_IN_DICTIONARY = "handyreader_dictionary"
}

object ApiPath {

    const val API_AUTH_TOKEN = "/api/v1/auth/token"

    const val API_FEEDBACK = "/api/v1/feedback"

    const val API_READ_BGS = "/api/v1/read-bg-textures"

    const val API_TTS_MODELS = "/api/v1/sherpa-models"

    const val API_TRANSLATE = "/api/v1/translate"

    const val API_TRANSLATE_LANGUAGES = "/api/v1/translate/languages"

    const val API_DICTIONARY = "/api/dictionary"
}

object ApiCode {
    const val CODE_SERV_ERROR = "CODE_SERV_ERROR"

    const val CODE_SERV_UNKOWN = "CODE_SERV_UNKOWN"

    const val CODE_SERIALIZATION_ERROR = "SERIALIZATION_ERROR"

    const val CODE_UNKNOWN_ERROR = "UNKNOWN_ERROR"

    const val CODE_TIME_OUT = "TIME_OUT"

    const val CODE_NETWORK_ERROR = "NETWORK_ERROR"

    const val CODE_DAILY_LIMIT = "DAILY_LIMIT"
}
