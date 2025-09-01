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
        return ReadingActiveEntity(
            date = readingActive.date,
            readingTime = readingActive.readingTime
        )
    }
}