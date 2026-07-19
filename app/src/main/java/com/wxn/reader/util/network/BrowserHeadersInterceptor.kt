package com.wxn.reader.util.network

import okhttp3.Interceptor
import okhttp3.Response

class BrowserHeadersInterceptor : Interceptor {

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36"
        private const val SEC_CH_UA =
            "\"Chromium\";v=\"136\", \"Google Chrome\";v=\"136\", \"Not.A/Brand\";v=\"99\""
        private const val SEC_CH_UA_PLATFORM = "\"Android\""
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("sec-ch-ua", SEC_CH_UA)
            .header("sec-ch-ua-mobile", "?1")
            .header("sec-ch-ua-platform", SEC_CH_UA_PLATFORM)
            .build()
        return chain.proceed(request)
    }
}
