package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wxn.reader.data.dto.OpdsCatalogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpdsCatalogDao {

    @Query("SELECT * FROM opds_catalogs WHERE isEnabled = 1 ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllEnabledCatalogs(): Flow<List<OpdsCatalogEntity>>

    @Query("SELECT * FROM opds_catalogs ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllCatalogs(): Flow<List<OpdsCatalogEntity>>

    @Query("SELECT * FROM opds_catalogs WHERE id = :id")
    suspend fun getCatalogById(id: Long): OpdsCatalogEntity?

    @Query("SELECT * FROM opds_catalogs WHERE predefinedId = :predefinedId")
    suspend fun getCatalogByPredefinedId(predefinedId: String): OpdsCatalogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalog(catalog: OpdsCatalogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCatalogs(catalogs: List<OpdsCatalogEntity>)

    @Update
    suspend fun updateCatalog(catalog: OpdsCatalogEntity)

    @Query("UPDATE opds_catalogs SET isEnabled = 0 WHERE id = :id")
    suspend fun disableCatalog(id: Long)

    @Query("DELETE FROM opds_catalogs WHERE id = :id")
    suspend fun deleteCatalog(id: Long)

    @Query("SELECT COUNT(*) FROM opds_catalogs WHERE predefinedId = :predefinedId")
    suspend fun catalogExistsByPredefinedId(predefinedId: String): Int
}
