package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.wxn.reader.data.dto.BookAnnotationEntity

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE deleted = 0")
    fun getAllAnnotations(): Flow<List<BookAnnotationEntity>>


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(annotation: BookAnnotationEntity): Long

    @Update
    suspend fun update(annotation: BookAnnotationEntity)

    @Delete
    suspend fun delete(annotation: BookAnnotationEntity)

    @Query("SELECT * FROM annotations WHERE bookId = :bookId AND deleted = 0")
    fun getAnnotationsForBook(bookId: Long): Flow<List<BookAnnotationEntity>>

    // ===== ★ 同步方案 §3.2.2 一期新增:per-row HLC + 软删 + 备份查询 =====
    /** 更新事件 HLC(按行 id)。mergeAnnotations 按 uuid LWW,需每条独立 HLC。 */
    @Query("UPDATE annotations SET syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE id = :id")
    suspend fun updateAnnotationHlcById(id: Long, l: Long, c: Int, d: String)

    /** 删除事件 HLC(按行 id,独立跟踪)。 */
    @Query("UPDATE annotations SET deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE id = :id")
    suspend fun updateAnnotationDeletedHlcById(id: Long, l: Long, c: Int, d: String)

    /** 按 uuid 查询(合并引擎用)。 */
    @Query("SELECT * FROM annotations WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): BookAnnotationEntity?

    /** 按 bookId 查全部(含软删),备份导出用。 */
    @Query("SELECT * FROM annotations WHERE bookId = :bookId")
    suspend fun getByBookIdIncludeDeleted(bookId: Long): List<BookAnnotationEntity>

    /** 软删一本书的所有标注(DeleteBookUseCase 改造)。 */
    @Query("UPDATE annotations SET deleted = 1, deletedHlcL = :l, deletedHlcC = :c, deletedHlcDevice = :d WHERE bookId = :bookId AND deleted = 0")
    suspend fun markDeletedByBook(bookId: Long, l: Long, c: Int, d: String): Int

    /** 复活一本书的标注(books 复活流程)。 */
    @Query("UPDATE annotations SET deleted = 0 WHERE bookId = :bookId AND deleted = 1")
    suspend fun reviveByBook(bookId: Long): Int

    /** UPSERT 或复活(被软删的同 uuid 行复活)。 */
    @Query("UPDATE annotations SET deleted = 0, syncHlcL = :l, syncHlcC = :c, syncHlcDevice = :d WHERE uuid = :uuid")
    suspend fun reviveByUuid(uuid: String, l: Long, c: Int, d: String): Int
}