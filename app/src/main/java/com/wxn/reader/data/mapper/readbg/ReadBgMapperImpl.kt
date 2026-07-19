package com.wxn.reader.data.mapper.readbg

import com.wxn.reader.data.dto.ReadBgEntity
import com.wxn.reader.domain.model.ReadBgData
import javax.inject.Inject

class ReadBgMapperImpl @Inject constructor(): ReadBgMapper {
    override fun toReadBgEntity(readBg: ReadBgData): ReadBgEntity {
        return ReadBgEntity(
            id = readBg.id,
            localPath = readBg.path,
            thumbnailPath = readBg.thumbnailUrl,
            remoteImageUrl = readBg.url,
            isDownloaded = readBg.isDownloaded,
            version = readBg.version,
            downloadedAt = System.currentTimeMillis()
        )
    }
    override fun toReadBg(readBgEntity: ReadBgEntity): ReadBgData {
        return ReadBgData(
            id = readBgEntity.id,
            path = readBgEntity.localPath,
            thumbnailUrl = readBgEntity.thumbnailPath,
            url = readBgEntity.remoteImageUrl,
            isDownloaded =  readBgEntity.isDownloaded,
            version = readBgEntity.version
        )
    }
}