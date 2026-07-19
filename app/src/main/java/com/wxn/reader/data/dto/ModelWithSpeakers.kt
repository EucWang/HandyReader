package com.wxn.reader.data.dto

import androidx.room.Embedded
import androidx.room.Relation

data class ModelWithSpeakers(
    @Embedded val model: SherpaModelEntity,
    @Relation(
        parentColumn = "name",
        entityColumn = "modelName"
    )
    val speakers: List<SherpaSpeakerEntity>
)