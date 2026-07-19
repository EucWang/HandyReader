package com.wxn.reader.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wxn.reader.data.dto.BookReadingTimeEntity

/**
 * book_reading_time DAO:per-book × per-device 的精确阅读时长。
 *
 * `incrementReadingTime` 改造后:`book_reading_time` 原子累加 + `refreshReadingTimeFromPerDevice` 重算 `books.readingTime` 派生值。
 *
 * ★ 同步方案 v2.6 §6.4.2.2 / §3.3。
 */
@Dao
interface BookReadingTimeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BookReadingTimeEntity)

    /**
     * 原子累加:命中则 `readingTimeMs = readingTimeMs + :delta`,否则 INSERT。
     * 单条 SQL 完成 read-free 累加(等价 INSERT ... ON CONFLICT ... DO UPDATE)。
     */
    @Query(
        "INSERT OR REPLACE INTO book_reading_time(bookId, deviceId, readingTimeMs, lastUpdated) " +
                "VALUES(:bookId, :deviceId, " +
                "COALESCE((SELECT readingTimeMs + :delta FROM book_reading_time " +
                "  WHERE bookId = :bookId AND deviceId = :deviceId), :delta), " +
                ":now)"
    )
    suspend fun incrementPerDevice(bookId: Long, deviceId: String, delta: Long, now: Long): Long

    @Query("SELECT * FROM book_reading_time WHERE bookId = :bookId AND deviceId = :deviceId")
    suspend fun getByBookIdAndDevice(bookId: Long, deviceId: String): BookReadingTimeEntity?

    @Query("SELECT * FROM book_reading_time WHERE bookId = :bookId")
    suspend fun getByBookId(bookId: Long): List<BookReadingTimeEntity>

    @Query("SELECT * FROM book_reading_time")
    suspend fun getAll(): List<BookReadingTimeEntity>

    /** 求某本书跨所有设备的累计时长。用于 refresh `books.readingTime` 派生值。 */
    @Query("SELECT COALESCE(SUM(readingTimeMs), 0) FROM book_reading_time WHERE bookId = :bookId")
    suspend fun sumByBookId(bookId: Long): Long

    @Query("DELETE FROM book_reading_time WHERE bookId = :bookId")
    suspend fun deleteByBookId(bookId: Long): Int
}
