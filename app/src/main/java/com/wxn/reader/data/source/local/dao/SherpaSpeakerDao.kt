package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.SherpaSpeakerEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface SherpaSpeakerDao {

    @Query("SELECT * FROM sherpa_speakers WHERE modelName = :modelName ORDER BY id ASC")
    fun getSpeakersByModel(modelName: String): Flow<List<SherpaSpeakerEntity>>

//    @Query("SELECT * FROM sherpa_speakers WHERE id = :id")
//    suspend fun getSpeakerById(id: Long): SherpaSpeakerEntity?


    @Query(
        """
        SELECT * FROM sherpa_speakers 
        WHERE modelName = :modelName 
        AND speakerName = :speakerName 
        LIMIT 1
        """
    )
    suspend fun getSpeakerByName(modelName: String, speakerName: String): SherpaSpeakerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeaker(speaker: SherpaSpeakerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeakers(speakers: List<SherpaSpeakerEntity>)

    @Query(
        """
        UPDATE sherpa_speakers 
        SET active = :active 
        WHERE modelName = :modelName 
        AND speakerName = :speakerName
        """
    )
    suspend fun updateSpeakerActive(modelName: String, speakerName: String, active: Boolean)

    @Query(
        """
        UPDATE sherpa_speakers 
        SET active = 0 
        WHERE modelName = :modelName
        """
    )
    suspend fun clearAllActiveForModel(modelName: String)

    @Query("DELETE FROM sherpa_speakers WHERE modelName = :modelName")
    suspend fun deleteSpeakersByModel(modelName: String)

//    @Query("DELETE FROM sherpa_speakers WHERE id = :id")
//    suspend fun deleteSpeaker(id: Long)

//    @Query("SELECT COUNT(*) FROM sherpa_speakers WHERE modelName = :modelName")
//    suspend fun getSpeakerCount(modelName: String): Int
}