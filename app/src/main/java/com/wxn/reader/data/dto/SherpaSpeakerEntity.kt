package com.wxn.reader.data.dto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index
@Entity(
    tableName = "sherpa_speakers",
    foreignKeys = [
        ForeignKey(
            entity = SherpaModelEntity::class,
            parentColumns = ["name"],
            childColumns = ["modelName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["modelName"], name = "idx_speaker_model_name"),
        Index(value = ["modelName", "speakerName"], name = "idx_speaker_model_speaker")
    ]
)
data class SherpaSpeakerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,                        // 自增主键

    val index: Int,
    val locale: String,

    val modelName: String,                   // 关联的模型名称 (外键)

    val speakerName: String,                 // Speaker 名称
    val gender: String,                      // 性别: "male", "female"
    val sampleVoice: String,                 // 示例音频 URL

    val active: Boolean = false              // 是否被选中
)