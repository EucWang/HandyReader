package com.wxn.reader.data.mapper.readingactive

import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.domain.model.ReadingActive
import javax.inject.Inject

class ReadingActiveMapperImpl @Inject constructor() : ReadingActiveMapper {
    override suspend fun toReadingActive(readingActiveEntity: ReadingActiveEntity): ReadingActive {
        return ReadingActive(
            date = readingActiveEntity.date,
            readingTime = readingActiveEntity.readingTime
        )
    }

    override suspend fun toReadingActiveEntity(readingActive: ReadingActive): ReadingActiveEntity {
        // ★ v9 同步方案:Entity 现含 deviceId(复合 PK)。本机场景由调用方 BooksRepositoryImpl 注入 deviceId;
        // 这里保留默认空串,旧路径不直接调用此方法(改用 toReadingActiveEntity(readingActive, deviceId) 重载)。
        return ReadingActiveEntity(
            date = readingActive.date,
            deviceId = "",
            readingTime = readingActive.readingTime
        )
    }
}