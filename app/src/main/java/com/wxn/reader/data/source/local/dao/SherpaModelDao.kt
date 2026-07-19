package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.wxn.reader.data.dto.ModelWithSpeakers
import com.wxn.reader.data.dto.SherpaModelEntity
import com.wxn.reader.data.dto.SherpaSpeakerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SherpaModelDao {


    /***
     * 获取已经下载好了的模型数据
     */
    @Transaction
    @Query("SELECT * FROM sherpa_models ORDER BY createdAt DESC")
    fun getModelsWithSpeakers(): Flow<List<ModelWithSpeakers>>


    @Query("SELECT * FROM sherpa_models WHERE name = :name")
    suspend fun getModelByName(name: String): SherpaModelEntity?

    @Query("SELECT * FROM sherpa_models WHERE locale = :locale ORDER BY createdAt DESC")
    fun getModelsByLocale(locale: String): Flow<List<SherpaModelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: SherpaModelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<SherpaModelEntity>)

    @Query("DELETE FROM sherpa_models WHERE name = :name")
    suspend fun deleteModel(name: String)

    @Query("SELECT COUNT(*) FROM sherpa_models WHERE name = :name")
    suspend fun modelExists(name: String): Int

    @Query("SELECT COUNT(*) FROM sherpa_models")
    suspend fun getDownloadedCount(): Int
}