package com.wxn.reader.domain.repository

import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import com.wxn.reader.data.model.FontCatalogItem
import kotlinx.coroutines.flow.Flow

interface FontRepository {

    suspend fun getCatalog(): List<FontCatalogItem>

    fun getAllFonts(): Flow<List<FontEntity>>

    suspend fun getFontById(fontId: String): FontEntity?

    suspend fun insertFont(font: FontEntity)

    suspend fun updateFont(font: FontEntity)

    suspend fun deleteFont(fontId: String)

    fun getFontFiles(fontId: String): Flow<List<FontFileEntity>>

    suspend fun getFontFilesSync(fontId: String): List<FontFileEntity>

    suspend fun getFontFileById(fileId: String): FontFileEntity?

    suspend fun getFontFileByVariant(fontId: String, variant: String): FontFileEntity?

    suspend fun insertFontFiles(files: List<FontFileEntity>)

    suspend fun updateFontFile(file: FontFileEntity)

    suspend fun deleteFontFiles(fontId: String)

    suspend fun getDownloadedVariantCount(fontId: String): Int

    fun getDownloadedFiles(): Flow<List<FontFileEntity>>

    suspend fun saveDownloadedFont(font: FontEntity, files: List<FontFileEntity>)
}
