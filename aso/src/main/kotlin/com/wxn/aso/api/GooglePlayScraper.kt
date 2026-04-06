package com.wxn.aso.api

import com.wxn.aso.model.CompetitorAnalysis
import com.wxn.aso.model.SimilarApp
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.datetime.Instant
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

class GooglePlayScraper(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private companion object {
        const val GOOGLE_PLAY_URL = "https://play.google.com/store/apps/details"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }

    suspend fun scrapeAppDetails(packageName: String): Result<CompetitorAnalysis> = withContext(Dispatchers.IO) {
        try {
            val url = "$GOOGLE_PLAY_URL?id=$packageName&hl=en"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Failed to fetch app details: ${response.code}")
                )
            }

            val html = response.body?.string()
            if (html.isNullOrEmpty()) {
                return@withContext Result.failure(
                    IOException("Empty response from Google Play")
                )
            }

            val analysis = parseHtmlToCompetitorAnalysis(html, packageName)
            Result.success(analysis)
        } catch (e: IOException) {
            Result.failure(IOException("Network error: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseHtmlToCompetitorAnalysis(html: String, packageName: String): CompetitorAnalysis {
        val doc = Jsoup.parse(html)
        
        val title = extractTitle(doc)
        val description = extractDescription(doc)
        val rating = extractRating(doc)
        val ratingsCount = extractRatingsCount(doc)
        val installs = extractInstalls(doc)
        val similarApps = extractSimilarApps(doc)
        val keywords = extractKeywords(doc)

        return CompetitorAnalysis(
            packageName = packageName,
            title = title,
            description = description,
            rating = rating,
            ratingsCount = ratingsCount,
            installs = installs,
            lastUpdated = Instant.now(),
            similarApps = similarApps,
            keywords = keywords
        )
    }

    private fun extractTitle(doc: Document): String {
        return doc.select("h1[itemprop=name]").first()?.text()?.trim()
            ?: doc.select("h1.AHM6Tu").first()?.text()?.trim()
            ?: "Unknown"
    }

    private fun extractDescription(doc: Document): String {
        return doc.select("div[itemprop=description]").first()?.text()?.trim()
            ?: doc.select("div.DWPxHb").first()?.text()?.trim()
            ?: ""
    }

    private fun extractRating(doc: Document): Float {
        val ratingText = doc.select("div[itemprop=ratingValue]").first()?.text()?.trim()
            ?: doc.select("div.BHMmbe").first()?.text()?.trim()
        return ratingText?.toFloatOrNull() ?: 0.0f
    }

    private fun extractRatingsCount(doc: Document): Long {
        val countText = doc.select("span[itemprop=ratingCount]").first()?.attr("content")
            ?: doc.select("span.EymY4b").first()?.text()?.trim()
        return countText?.replace("[,\\s]".toRegex(), "")?.toLongOrNull() ?: 0L
    }

    private fun extractInstalls(doc: Document): String {
        return doc.select("span[itemprop=numDownloads]").first()?.attr("content")
            ?: doc.select("div.wVdtUb").first()?.text()?.trim()
            ?: "1,000+"
    }

    private fun extractSimilarApps(doc: Document): List<SimilarApp> {
        return emptyList()
    }

    private fun extractKeywords(doc: Document): List<String> {
        val metaKeywords = doc.select("meta[name=keywords]").first()?.attr("content")
        return metaKeywords?.split(",")?.map { it.trim() }?.take(10) ?: emptyList()
    }
}
