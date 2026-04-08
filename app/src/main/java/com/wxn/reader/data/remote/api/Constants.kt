package com.wxn.reader.data.remote.api


object Constants {

    const val BASE_URL = com.wxn.reader.BuildConfig.FEEDBACK_API_URL

}

object ApiPath {

    const val API_FEEDBACK = "/api/v1/feedback"

    const val API_READ_BGS = "/api/v1/read-bg-textures"
}

object ApiCode {
    const val CODE_SERV_ERROR = "CODE_SERV_ERROR"

    const val CODE_SERV_UNKOWN = "CODE_SERV_UNKOWN"

    const val CODE_SERIALIZATION_ERROR = "SERIALIZATION_ERROR"

    const val CODE_UNKNOWN_ERROR = "UNKNOWN_ERROR"
}
