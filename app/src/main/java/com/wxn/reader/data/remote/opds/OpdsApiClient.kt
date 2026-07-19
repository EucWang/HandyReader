package com.wxn.reader.data.remote.opds

import android.content.Context
import android.util.Base64
import com.wxn.base.util.Logger
import com.wxn.reader.data.model.opds.OpdsFeed
import com.wxn.reader.data.source.local.OpdsCredentialStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import kotlin.coroutines.resumeWithException

class OpdsApiClient @Inject constructor(
    @ApplicationContext val context: Context,
    @Named("DownloadOkHttpClient") private val okHttpClient: OkHttpClient,
    private val credentialStore: OpdsCredentialStore
) {
    private val feedCache = ConcurrentHashMap<String, CachedFeed>()
    private val resolvedSearchTemplates = ConcurrentHashMap<Long, String>()

    private data class CachedFeed(
        val feed: OpdsFeed,
        val cachedAt: Long = System.currentTimeMillis()
    ) {
        val isExpired: Boolean
            get() = (System.currentTimeMillis() - cachedAt) > CACHE_TTL_MS
    }

    companion object {
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
        private const val CACHE_MAX_SIZE = 30
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cont.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response, null)
            }
        })
    }

    suspend fun fetchFeed(
        url: String,
        catalogId: Long,
        useCache: Boolean = true
    ): Result<OpdsFeed> = withContext(Dispatchers.IO) {
        if (useCache) {
            feedCache[url]?.takeIf { !it.isExpired }?.let {
                Logger.d("OpdsApiClient::fetchFeed: cache hit for $url")
                return@withContext Result.success(it.feed)
            }
        }

        try {
            val requestBuilder = Request.Builder().url(url)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )

            val credentials = credentialStore.getCredentials(catalogId)
            if (credentials != null) {
                val auth = Base64.encodeToString(
                    "${credentials.first}:${credentials.second}".toByteArray(),
                    Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", "Basic $auth")
            }

            val call = okHttpClient.newCall(requestBuilder.build())
            val response = call.await()

            when {
                response.code == HttpURLConnection.HTTP_UNAUTHORIZED -> {
                    response.close()
                    Logger.w("OpdsApiClient::fetchFeed: 401 Unauthorized for catalogId=$catalogId")
                    return@withContext Result.failure(OpdsAuthException(catalogId))
                }

                response.code == HttpURLConnection.HTTP_FORBIDDEN -> {
                    val msg = "HTTP 403: Forbidden"
                    response.close()
                    Logger.w("OpdsApiClient::fetchFeed: $msg for url=$url")
                    return@withContext Result.failure(OpdsNetworkException(403, msg))
                }

                !response.isSuccessful -> {
                    val msg = "HTTP ${response.code}: ${response.message}"
                    response.close()
                    Logger.w("OpdsApiClient::fetchFeed: $msg for url=$url")
                    return@withContext Result.failure(OpdsNetworkException(response.code, msg))
                }

                else -> {
                    val contentType = response.header("Content-Type", "") ?: ""
                    val isXml = contentType.contains("xml", ignoreCase = true)
                            || contentType.contains("atom", ignoreCase = true)

                    val body = response.body
                        ?: throw IOException("Empty response body")
                    val xml = body.string()
                    response.close()

                    if (!isXml) {
                        val trimmed = xml.trimStart()
                        val isActuallyXml = trimmed.startsWith("<?xml", ignoreCase = true)
                                || trimmed.startsWith("<feed", ignoreCase = true)
                        if (!isActuallyXml) {
                            val snippet = xml.take(200)
                            Logger.w("OpdsApiClient::fetchFeed: non-XML response for url=$url, Content-Type=$contentType, snippet=$snippet")
                            return@withContext Result.failure(
                                OpdsContentTypeException(contentType, url)
                            )
                        }
                        Logger.d("OpdsApiClient::fetchFeed: Content-Type=$contentType but body is XML, attempting parse for url=$url")
                    }

                    val feed = OpdsFeedParser.parse(context, xml, catalogId, baseUrl = url)
                    Logger.d("OpdsApiClient::fetchFeed: parsed ${feed.entries.size} entries from $url")

                    val effectiveFeed = if (feed.isOpenSearchDescription) {
                        val cached = resolvedSearchTemplates[catalogId]
                        if (cached != null) {
                            feed.copy(searchUrl = cached, searchType = "application/opensearch+template")
                        } else {
                            resolveOpenSearchDescription(feed, catalogId, url)
                                .onSuccess { resolved ->
                                    resolved.searchUrl?.let { resolvedSearchTemplates[catalogId] = it }
                                }
                                .getOrElse { feed }
                        }
                    } else {
                        feed
                    }

                    cacheFeed(url, effectiveFeed)
                    return@withContext Result.success(effectiveFeed)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpdsAuthException) {
            Result.failure<OpdsFeed>(e)
        } catch (e: Exception) {
            Logger.e("OpdsApiClient::fetchFeed: error for url=$url: $e")
            Result.failure<OpdsFeed>(e)
        }
    }

    private suspend fun resolveOpenSearchDescription(
        feed: OpdsFeed,
        catalogId: Long,
        baseUrl: String
    ): Result<OpdsFeed> {
        val searchDocUrl = feed.searchUrl ?: return Result.failure(Exception("No search URL"))
        Logger.d("OpdsApiClient::resolveOpenSearchDescription: fetching from $searchDocUrl")
        return try {
            val requestBuilder = Request.Builder().url(searchDocUrl)
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )

            val credentials = credentialStore.getCredentials(catalogId)
            if (credentials != null) {
                val auth = Base64.encodeToString(
                    "${credentials.first}:${credentials.second}".toByteArray(),
                    Base64.NO_WRAP
                )
                requestBuilder.header("Authorization", "Basic $auth")
            }

            val call = okHttpClient.newCall(requestBuilder.build())
            val response = call.await()
            if (!response.isSuccessful) {
                response.close()
                return Result.failure(Exception("Failed to fetch OpenSearch description"))
            }
            val body = response.body ?: run {
                response.close()
                return Result.failure(Exception("Empty response"))
            }
            val xml = body.string()
            response.close()

            val templateUrl = parseOpenSearchTemplate(xml)
            if (templateUrl != null) {
                val resolvedUrl = OpdsFeedParser.resolveUrl(context, searchDocUrl, templateUrl)
                Result.success(
                    feed.copy(
                        searchUrl = resolvedUrl,
                        searchType = "application/opensearch+template"
                    )
                )
            } else {
                Result.failure(Exception("Could not extract search template"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e("OpdsApiClient::resolveOpenSearchDescription: error: $e")
            Result.failure(e)
        }
    }

    private fun parseOpenSearchTemplate(xml: String): String? {
        try {
            val normalized = OpdsFeedParser.normalizeNamespaces(xml)
            val parser = android.util.Xml.newPullParser()
            parser.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(java.io.StringReader(normalized))
            var fallbackTemplate: String? = null
            while (parser.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "Url") {
                    val type = parser.getAttributeValue(null, "type")
                    val template = parser.getAttributeValue(null, "template")
                    if (template != null && type != null) {
                        when {
                            type.contains("atom") -> return template
                            type.contains("xml") && !type.contains("opensearchdescription") && fallbackTemplate == null ->
                                fallbackTemplate = template
                        }
                    }
                }
            }
            return fallbackTemplate
        } catch (_: Exception) {
        }
        return null
    }

    private fun cacheFeed(url: String, feed: OpdsFeed) {
        if (feedCache.size >= CACHE_MAX_SIZE) {
            val oldest = feedCache.minByOrNull { it.value.cachedAt }?.key
            oldest?.let { feedCache.remove(it) }
        }
        feedCache[url] = CachedFeed(feed)
    }

    fun clearCache() {
        feedCache.clear()
        resolvedSearchTemplates.clear()
    }

    suspend fun search(
        searchUrl: String,
        catalogId: Long,
        query: String,
        startIndex: Int = 0,
        count: Int = 50
    ): Result<OpdsFeed> {
        val url = OpdsFeedParser.buildSearchUrl(searchUrl, query, startIndex, count)
        return fetchFeed(url, catalogId, useCache = false)
    }

    fun buildAuthHeader(catalogId: Long): String? {
        val credentials = credentialStore.getCredentials(catalogId) ?: return null
        return "Basic " + Base64.encodeToString(
            "${credentials.first}:${credentials.second}".toByteArray(),
            Base64.NO_WRAP
        )
    }
}