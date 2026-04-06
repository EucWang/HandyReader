package com.wxn.aso.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CompetitorAnalysis(
    val packageName: String,
    val title: String,
    val description: String,
    val rating: Float,
    val ratingsCount: Long,
    val installs: String,
    val lastUpdated: Instant,
    val similarApps: List<SimilarApp>,
    val keywords: List<String>,
    val screenshots: List<String> = emptyList()
) {
    val installsNumeric: Long? get() {
        return installs.removeSuffix("+").replace(",", "").toLongOrNull()
    }
}

@Serializable
data class SimilarApp(
    val packageName: String,
    val title: String,
    val rating: Float,
    val installs: String
)

@Serializable
data class CompetitorCheckResult(
    val competitorAnalysis: CompetitorAnalysis?,
    val score: Int,
    val maxScore: Int = 20,
    val issues: List<Issue> = emptyList()
)
