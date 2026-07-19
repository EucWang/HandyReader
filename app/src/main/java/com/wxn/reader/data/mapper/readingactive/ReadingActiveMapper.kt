package com.wxn.reader.data.mapper.readingactive

import com.wxn.reader.data.dto.ReadingActiveEntity
import com.wxn.reader.domain.model.ReadingActive

interface ReadingActiveMapper {

    suspend fun toReadingActive(readingActiveEntity: ReadingActiveEntity): ReadingActive

    suspend fun toReadingActiveEntity(readingActive: ReadingActive): ReadingActiveEntity
}