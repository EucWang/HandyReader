package com.wxn.reader.data.source.local.dao

import androidx.room.Dao

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.ReadBgEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadBgDao {

    /***
     * 获取所有已下载的图片
     */
    @Query("SELECT * FROM read_bgs WHERE isDownloaded = 1")
    fun getAllDownloaded(): Flow<List<ReadBgEntity>>

    /***
     * 根据id查找图片数据实体
     */
    @Query("SELECT * FROM read_bgs WHERE id = :id")
    suspend fun getDownloadById(id: String): ReadBgEntity?

    /***
     * 插入图片数据
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(texture: ReadBgEntity)

    /***
     * 根据id删除图片
     */
    @Query("DELETE FROM read_bgs WHERE id = :id")
    suspend fun deleteById(id: String)

}