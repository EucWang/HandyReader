package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wxn.reader.data.dto.FontEntity
import com.wxn.reader.data.dto.FontFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FontDao {

    @Query("SELECT * FROM fonts ORDER BY createdAt ASC")
    fun getAllFonts(): Flow<List<FontEntity>>

    @Query("SELECT * FROM fonts WHERE id = :fontId")
    suspend fun getFontById(fontId: String): FontEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFont(font: FontEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFonts(fonts: List<FontEntity>)

    @Update
    suspend fun updateFont(font: FontEntity)

    @Query("DELETE FROM fonts WHERE id = :fontId")
    suspend fun deleteFont(fontId: String)

    @Query("SELECT * FROM font_files WHERE fontId = :fontId")
    fun getFontFiles(fontId: String): Flow<List<FontFileEntity>>

    @Query("SELECT * FROM font_files WHERE fontId = :fontId")
    suspend fun getFontFilesSync(fontId: String): List<FontFileEntity>

    @Query("SELECT * FROM font_files WHERE id = :fileId")
    suspend fun getFontFileById(fileId: String): FontFileEntity?

    @Query("SELECT * FROM font_files WHERE fontId = :fontId AND variant = :variant")
    suspend fun getFontFileByVariant(fontId: String, variant: String): FontFileEntity?

    @Query("SELECT * FROM font_files WHERE localPath IS NOT NULL")
    fun getDownloadedFiles(): Flow<List<FontFileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFontFiles(files: List<FontFileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFontFile(file: FontFileEntity)

    @Update
    suspend fun updateFontFile(file: FontFileEntity)

    @Query("DELETE FROM font_files WHERE fontId = :fontId")
    suspend fun deleteFontFiles(fontId: String)

    @Query("SELECT COUNT(*) FROM font_files WHERE fontId = :fontId AND localPath IS NOT NULL")
    suspend fun getDownloadedVariantCount(fontId: String): Int
}
