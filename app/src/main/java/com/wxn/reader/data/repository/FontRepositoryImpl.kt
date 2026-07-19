package com.wxn.reader.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.Logger
import com.wxn.base.util.PathUtil
import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import com.wxn.reader.data.model.FontCatalogItem
import com.wxn.reader.data.model.FontCatalogParser
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.dao.FontDao
import com.wxn.reader.domain.repository.FontRepository
import com.wxn.reader.util.download.OKHttpStringStreamer
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FontRepositoryImpl @Inject constructor(
    private val fontDao: FontDao,
    private val appDatabase: AppDatabase,
    private val context: Context,
    private val okHttpStringStreamer: OKHttpStringStreamer
) : FontRepository {

    @Volatile
    private var cachedCatalog: List<FontCatalogItem>? = null

    private val catalogFile: File by lazy {
        File(
            PathUtil.getDownloadDir(context, DownloadFileType.FONT),
            FontCatalogParser.FONTS_CATALOG_CACHE_FILE
        )
    }

    override suspend fun getCatalog(): List<FontCatalogItem> {
        cachedCatalog?.let { return it }

        if (isLocalCacheValid()) {
            try {
                val json = catalogFile.readText()
                val catalog = FontCatalogParser.parseFromJson(json)
                cachedCatalog = catalog.fonts
                return catalog.fonts
            } catch (e: Exception) {
                Logger.e("FontRepository: Failed to read cached catalog：$e")
            }
        }

        return fetchFromRemote()
    }

    private fun isLocalCacheValid(): Boolean {
        if (!catalogFile.exists()) return false
        val threeDaysMillis = TimeUnit.DAYS.toMillis(3)
        return (System.currentTimeMillis() - catalogFile.lastModified()) <= threeDaysMillis
    }

    private suspend fun fetchFromRemote(): List<FontCatalogItem> {
        val result = okHttpStringStreamer.getStringFromUrl(FontCatalogParser.FONTS_CATALOG_URL)
        return result.fold(
            onSuccess = { jsonString ->
                try {
                    val catalog = FontCatalogParser.parseFromJson(jsonString)
                    saveLocalCache(jsonString)
                    cachedCatalog = catalog.fonts
                    catalog.fonts
                } catch (e: Exception) {
                    Logger.e("FontRepository: Failed to parse remote catalog: $e")
                    fallbackToLocalCache()
                }
            },
            onFailure = { e ->
                Logger.e("FontRepository: Failed to fetch remote catalog: $e")
                fallbackToLocalCache()
            }
        )
    }

    private fun saveLocalCache(jsonString: String) {
        try {
            catalogFile.parentFile?.mkdirs()
            catalogFile.writeText(jsonString)
        } catch (e: Exception) {
            Logger.e("FontRepository: Failed to save local cache: $e")
        }
    }

    private fun fallbackToLocalCache(): List<FontCatalogItem> {
        if (catalogFile.exists()) {
            try {
                val json = catalogFile.readText()
                val catalog = FontCatalogParser.parseFromJson(json)
                cachedCatalog = catalog.fonts
                return catalog.fonts
            } catch (e: Exception) {
                Logger.e("FontRepository: Failed to read fallback cache: $e")
            }
        }
        return emptyList()
    }

    override fun getAllFonts(): Flow<List<FontEntity>> {
        return fontDao.getAllFonts()
    }

    override suspend fun getFontById(fontId: String): FontEntity? {
        return fontDao.getFontById(fontId)
    }

    override suspend fun insertFont(font: FontEntity) {
        fontDao.insertFont(font)
    }

    override suspend fun updateFont(font: FontEntity) {
        fontDao.updateFont(font)
    }

    override suspend fun deleteFont(fontId: String) {
        fontDao.deleteFont(fontId)
    }

    override fun getFontFiles(fontId: String): Flow<List<FontFileEntity>> {
        return fontDao.getFontFiles(fontId)
    }

    override suspend fun getFontFilesSync(fontId: String): List<FontFileEntity> {
        return fontDao.getFontFilesSync(fontId)
    }

    override suspend fun getFontFileById(fileId: String): FontFileEntity? {
        return fontDao.getFontFileById(fileId)
    }

    override suspend fun getFontFileByVariant(fontId: String, variant: String): FontFileEntity? {
        return fontDao.getFontFileByVariant(fontId, variant)
    }

    override suspend fun insertFontFiles(files: List<FontFileEntity>) {
        fontDao.insertFontFiles(files)
    }

    override suspend fun updateFontFile(file: FontFileEntity) {
        fontDao.updateFontFile(file)
    }

    override suspend fun deleteFontFiles(fontId: String) {
        fontDao.deleteFontFiles(fontId)
    }

    override suspend fun getDownloadedVariantCount(fontId: String): Int {
        return fontDao.getDownloadedVariantCount(fontId)
    }

    override fun getDownloadedFiles(): Flow<List<FontFileEntity>> {
        return fontDao.getDownloadedFiles()
    }

    override suspend fun saveDownloadedFont(font: FontEntity, files: List<FontFileEntity>) {
        appDatabase.withTransaction {
            fontDao.insertFont(font)
            fontDao.insertFontFiles(files)
        }
    }
}
