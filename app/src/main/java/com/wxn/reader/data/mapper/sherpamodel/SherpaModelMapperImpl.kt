package com.wxn.reader.data.mapper.sherpamodel

import com.wxn.reader.data.dto.ModelWithSpeakers
import com.wxn.reader.data.dto.SherpaModelEntity
import com.wxn.reader.data.dto.SherpaSpeakerEntity
import com.wxn.reader.domain.model.TTSModelData
import com.wxn.reader.util.tts.data.Speaker
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SherpaModelMapperImpl @Inject constructor() : SherpaModelMapper {

    override fun toTTSModelData(modelWithSpeakers: ModelWithSpeakers): TTSModelData {
        return toTTSModelData(modelWithSpeakers.model, modelWithSpeakers.speakers)
    }
    override fun toSherpaModelEntity(model: TTSModelData): SherpaModelEntity {
        return SherpaModelEntity(
            name = model.name,
            url = model.url,
            type = model.type,
            locale = model.locale,
            size = model.size,
            speakersNum = model.speakers_num,
            processSpeed = model.processSpeed,
            quality = model.quality,
            baseDatas = Json.encodeToString(model.base ?: emptyList()),
            createdAt = System.currentTimeMillis(),
            license = model.license,
            licenseUrl = model.licenseUrl,
            remark = model.remark
        )
    }


    override fun toTTSModelData(
        modelEntity: SherpaModelEntity,
        speakerEntities: List<SherpaSpeakerEntity>
    ): TTSModelData {
        return TTSModelData(
            name = modelEntity.name,
            url = modelEntity.url,
            type = modelEntity.type,
            locale = modelEntity.locale,
            size = modelEntity.size,
            base = Json.decodeFromString(modelEntity.baseDatas),
            processSpeed = modelEntity.processSpeed,
            quality = modelEntity.quality,
            speakers_num = modelEntity.speakersNum,
            speakers = toSpeakers(speakerEntities),
            license = modelEntity.license,
            licenseUrl = modelEntity.licenseUrl,
            remark = modelEntity.remark
        )
    }

    override fun toSherpaSpeakerEntities(
        modelName: String,
        speakers: List<Speaker>
    ): List<SherpaSpeakerEntity> {
        return speakers.map { speaker ->
            SherpaSpeakerEntity(
                index = speaker.index,
                locale = speaker.locale,
                modelName = modelName,
                speakerName = speaker.name,
                gender = speaker.gender,
                sampleVoice = speaker.sampleVoice,
                active = speaker.active
            )
        }
    }


    override fun toSpeakers(
        speakerEntities: List<SherpaSpeakerEntity>
    ): List<Speaker> {
        return speakerEntities.map { entity ->
            Speaker(
                id = "${entity.modelName}_${entity.speakerName}",
                index = entity.index,
                name = entity.speakerName,
                gender = entity.gender,
                locale = entity.locale,
                description = "",
                active = entity.active,
                sampleVoice = entity.sampleVoice
            )
        }
    }
}