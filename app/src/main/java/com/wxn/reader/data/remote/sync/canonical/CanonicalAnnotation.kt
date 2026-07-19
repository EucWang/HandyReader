package com.wxn.reader.data.remote.sync.canonical

import kotlinx.serialization.Serializable

/** 标注动机(对应本地 [com.wxn.reader.domain.model.AnnotationType] 的 HIGHLIGHT/UNDERLINE)。 */
@Serializable
enum class AnnotationMotivation {
    HIGHLIGHTING,
    UNDERLINING,
}

/** 标注 body 文本(笔记/高亮文字)。 */
@Serializable
data class AnnotationBody(
    val text: String? = null,
    val format: String? = null,
)

/**
 * Canonical 标注 Record(高亮 / 下划线)。
 *
 * 合并语义:uuid 并集 + 同 uuid LWW + 删除墓碑。
 *
 * ★ 同步方案 v2.6 §2.4.2 / 一期 §3.3 mergeAnnotations。
 */
@Serializable
data class CanonicalAnnotation(
    override val uuid: String,
    override val hlc: HlcTs,
    override val deleted: Boolean,
    override val schemaVersion: Int = 2,
    val motivation: AnnotationMotivation,
    val locator: String,
    val color: String,
    val body: AnnotationBody? = null,
) : SyncRecord
