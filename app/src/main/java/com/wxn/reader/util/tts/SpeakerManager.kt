package com.wxn.reader.util.tts

import com.wxn.reader.util.tts.data.SpeakerInfo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json

class SpeakerManager {

    val client: HttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 300 * 1000L
            connectTimeoutMillis = 300 * 1000L
            socketTimeoutMillis = Long.MAX_VALUE
        }
    }

    suspend fun list(): Result<List<SpeakerInfo>>    {
//        val client = HttpClient {
//            install(ContentNegotiation) {
////                GsonSerializer()
//            }
//        }

        val res = client.get(buildUrl()) {
            header("sec-ch-ua-platform", "macOS")
            header("User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0")
            header(
                "sec-ch-ua",
                "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\""
            )
            header("sec-ch-ua-mobile", "?0")
            header("Accept", "*/*")
            header("X-Edge-Shopping-Flag", "1")
            header("Sec-MS-GEC", DRM.genSecMsGec())
            header("Sec-MS-GEC-Version", "1-131.0.2903.70")
            header("Sec-Fetch-Site", "none")
            header("Sec-Fetch-Mode", "cors")
            header("Sec-Fetch-Dest", "empty")
            header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6")
            header("Accept-Encoding", "gzip, deflate, br, zstd")
        }

        if (!res.status.isSuccess()) {
            return Result.failure(Throwable(res.status.description))
        }

//        client.close()

        val text = res.bodyAsText()
        val obj = Json.decodeFromString<List<SpeakerInfo>>(text)
        return Result.success(obj)
    }


    private fun buildUrl(): String {
        return "https://speech.platform.bing.com/consumer/speech/synthesize/readaloud/voices/list?trustedclienttoken=6A5AA1D4EAFF4E9FB37E23D68491D6F4&Sec-MS-GEC=${DRM.genSecMsGec()}&Sec-MS-GEC-Version=1-131.0.2903.70"
    }
}