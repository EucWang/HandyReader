package com.wxn.reader.di

import com.wxn.reader.BuildConfig
import com.wxn.reader.data.remote.api.FeedbackApi
import com.wxn.reader.data.remote.api.FeedbackApiImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.accept
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true  // 序列化包含默认值的字段
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        // 使用 OkHttp 引擎
        engine {
            // OkHttp 特定配置
            config {
                // 连接池配置
                connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                // 拦截器（可选）
                addInterceptor { chain ->
                    val original = chain.request()
                    val request = original.newBuilder()
                        .header("Accept", "application/json")
                        .method(original.method, original.body)
                        .build()
                    chain.proceed(request)
                }

                // 缓存配置（可选）
//                cache(
//                    Cache(
//                        directory = File(cacheDir, "http-cache"),
//                        maxSize = 10L * 1024 * 1024 // 10MB
//                    )
//                )
            }
        }

        // 内容协商
        install(ContentNegotiation) {
            json(json)
        }

        // 日志（仅 Debug 构建）
        install(Logging) {
            level = if (BuildConfig.DEBUG) {
                LogLevel.ALL
            } else {
                LogLevel.NONE
            }
            logger = object : Logger {
                override fun log(message: String) {
                    com.wxn.base.util.Logger.d("KtorClient:$message")
                }
            }
        }

        // 超时配置
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000      // 请求超时 30秒
            connectTimeoutMillis = 10_000     // 连接超时 10秒
            socketTimeoutMillis = 30_000      // Socket 超时 30秒
        }

        // 重试配置
        install(HttpRequestRetry) {
            maxRetries = 3
            retryOnExceptionIf { request, exception ->
                exception is IOException ||
                        exception is SocketTimeoutException
            }
            retryIf { request, response ->
                response.status.value in 500..599
            }
            exponentialDelay()  // 指数退避
        }

        // 默认请求头
        defaultRequest {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }
    }

    @Provides
    @Singleton
    fun provideFeedbackApi(httpClient: HttpClient): FeedbackApi {
        return FeedbackApiImpl(httpClient)
    }
}