package com.wxn.reader.data.mapper.sherpamodel

import com.wxn.reader.data.dto.ModelWithSpeakers
import com.wxn.reader.data.dto.SherpaModelEntity
import com.wxn.reader.data.dto.SherpaSpeakerEntity
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.util.tts.data.Speaker


interface SherpaModelMapper {

    fun toTTSModelData(modelWithSpeakers: ModelWithSpeakers): TTSModelData

    fun toSherpaModelEntity(model: TTSModelData): SherpaModelEntity

    fun toTTSModelData(
        modelEntity: SherpaModelEntity,
        speakerEntities: List<SherpaSpeakerEntity>
    ): TTSModelData

    fun toSherpaSpeakerEntities(
        modelName: String,
        speakers: List<Speaker>
    ): List<SherpaSpeakerEntity>

    fun toSpeakers(
        speakerEntities: List<SherpaSpeakerEntity>
    ): List<Speaker>
}