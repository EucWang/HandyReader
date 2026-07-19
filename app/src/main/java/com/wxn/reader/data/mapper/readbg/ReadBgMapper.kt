package com.wxn.reader.data.mapper.readbg

import com.wxn.reader.data.dto.ReadBgEntity
import com.wxn.reader.domain.model.ReadBgData

interface ReadBgMapper {

    fun toReadBgEntity(readBg: ReadBgData) : ReadBgEntity

    fun toReadBg(readBgEntity: ReadBgEntity) : ReadBgData
}