package com.wxn.reader.domain.model

import kotlinx.serialization.Serializable



/***
 * TTS Model Dependency Model  Metadata Infos
 */
@Serializable
data class DependencyIndex(
    val dependencies: Map<String, DependencyMetadata> // key: url
)