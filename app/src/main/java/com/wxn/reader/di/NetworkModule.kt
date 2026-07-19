package com.wxn.reader.di

import android.content.Context
import androidx.work.WorkManager
import com.wxn.reader.BuildConfig
import com.wxn.reader.data.remote.api.FeedbackApi
import com.wxn.reader.data.remote.api.FeedbackApiImpl
import com.wxn.reader.data.remote.api.ReadBgsApi
import com.wxn.reader.data.remote.api.ReadBgsApiImpl
import com.wxn.reader.data.remote.api.TTSModelsApi
import com.wxn.reader.data.remote.api.TTSModelsApiImpl
import com.wxn.reader.data.remote.api.TranslateApi
import com.wxn.reader.data.remote.api.TranslateApiImpl
import com.wxn.reader.data.remote.api.DictionaryApi
import com.wxn.reader.data.remote.api.DictionaryApiImpl
import com.wxn.reader.data.remote.auth.AuthInterceptor
import com.wxn.reader.data.remote.auth.TokenManager
import com.wxn.reader.data.source.local.dao.DownloadHistoryDao
import com.wxn.reader.util.download.FileDownloadManager
import com.wxn.reader.util.download.IDownloader
import com.wxn.reader.util.download.IDownloaderWithResume
import com.wxn.reader.util.download.OKHttpStringStreamer
import com.wxn.reader.util.download.OkHttpDownloader
import com.wxn.reader.util.download.OkHttpDownloaderWithResume
import com.wxn.reader.util.download.ZipExtractor
import com.wxn.reader.util.network.BrowserHeadersInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.inject.Named
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
    fun provideHttpClient(json: Json, tokenManager: TokenManager): HttpClient = HttpClient(OkHttp) {
        // 使用 OkHttp 引擎
        engine {
            // OkHttp 特定配置
            config {
                // 连接池配置
                connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                // JWT Auth 拦截器
                addInterceptor(AuthInterceptor(tokenManager))
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

    @Provides
    @Singleton
    fun provideReadBgsApi(httpClient: HttpClient): ReadBgsApi {
        return ReadBgsApiImpl(httpClient)
    }

    @Provides
    @Singleton
    fun provideTTSModelsListApi(httpClient: HttpClient): TTSModelsApi {
        return TTSModelsApiImpl(httpClient)
    }

    @Provides
    @Singleton
    fun provideTranslateApi(httpClient: HttpClient): TranslateApi {
        return TranslateApiImpl(httpClient)
    }

    @Provides
    @Singleton
    fun provideDictionaryApi(httpClient: HttpClient): DictionaryApi {
        return DictionaryApiImpl(httpClient)
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideFileDownloaderManager(
        @ApplicationContext context: Context,
        workManager: WorkManager,
        zipExtractor: ZipExtractor,
        downloadHistoryDao: DownloadHistoryDao
    ): FileDownloadManager {
        return FileDownloadManager(context, workManager, zipExtractor, downloadHistoryDao)
    }

    /***
     * For downloader
     */
    @Provides
    @Singleton
    @Named("DownloadOkHttpClient")
    fun provideDownloadOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BrowserHeadersInterceptor())
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /***
     * For token requests
     */
    @Provides
    @Singleton
    @Named("TokenOkHttpClient")
    fun provideTokenOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideOkHttpDownloader(
        @Named("DownloadOkHttpClient") okHttpClient: OkHttpClient
    ): IDownloader = OkHttpDownloader(okHttpClient)

    @Provides
    @Singleton
    fun provideOkHttpDownloaderWithResume(
        @Named("DownloadOkHttpClient") okHttpClient: OkHttpClient
    ): IDownloaderWithResume = OkHttpDownloaderWithResume(okHttpClient)

    @Provides
    @Singleton
    fun provideOKHttpStringStreamer(
        @Named("DownloadOkHttpClient") okHttpClient: OkHttpClient
    ): OKHttpStringStreamer = OKHttpStringStreamer(okHttpClient)

}