package com.wxn.aso.data

import com.wxn.aso.model.CompetitorAnalysis
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

class CompetitorCache(
    private val cacheDir: File,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val ttl: Duration = 7.days
) {
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    fun get(packageName: String): CompetitorAnalysis? {
        val cacheFile = getCacheFile(packageName)
        if (!cacheFile.exists()) return null
        
        val metadataFile = getMetadataFile(packageName)
        if (!metadataFile.exists()) return null
        
        return try {
            val metadata = metadataFile.readText().toLong()
            val cachedTime = Instant.fromEpochMilliseconds(metadata)
            val now = Clock.System.now()
            
            if ((now - cachedTime) > ttl) {
                cacheFile.delete()
                metadataFile.delete()
                return null
            }
            
            val cachedData = cacheFile.readText()
            json.decodeFromString<CompetitorAnalysis>(cachedData)
        } catch (e: Exception) {
            cacheFile.delete()
            metadataFile.delete()
            null
        }
    }

    fun put(packageName: String, analysis: CompetitorAnalysis) {
        val cacheFile = getCacheFile(packageName)
        val metadataFile = getMetadataFile(packageName)
        
        try {
            val serialized = json.encodeToString(analysis)
            cacheFile.writeText(serialized)
            
            val now = Clock.System.now().toEpochMilliseconds()
            metadataFile.writeText(now.toString())
        } catch (e: Exception) {
            // 忽略缓存写入错误
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun getCacheFile(packageName: String): File {
        val safeName = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(cacheDir, "$safeName.json")
    }

    private fun getMetadataFile(packageName: String): File {
        val safeName = packageName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(cacheDir, "$safeName.meta")
    }
}
