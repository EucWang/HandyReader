package com.wxn.aso.checker

import com.wxn.aso.api.GooglePlayScraper
import com.wxn.aso.data.CompetitorCache
import com.wxn.aso.model.CheckCategory
import com.wxn.aso.model.CheckResult
import com.wxn.aso.model.CompetitorAnalysis
import com.wxn.aso.model.Issue
import com.wxn.aso.model.Severity
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import java.io.File

class CompetitorCheck(
    private val projectRoot: File,
    private val scraper: GooglePlayScraper,
    private val cache: CompetitorCache
) {
    private companion object {
        const val MAX_SCORE = 20
        const val RATING_WEIGHT = 6
        const val INSTALLS_WEIGHT = 6
        const val KEYWORDS_WEIGHT = 4
        const val FRESHNESS_WEIGHT = 4
        
        const val MIN_RATING = 4.0f
        const val MIN_INSTALLS = 1000000L
        const val MIN_KEYWORDS_MATCH = 3
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val issues = mutableListOf<Issue>()
        
        val configFile = File(projectRoot, "aso-config.json")
        val competitorPackages = if (configFile.exists()) {
            val configText = configFile.readText()
            extractCompetitorPackages(configText)
        } else {
            emptyList()
        }
        
        if (competitorPackages.isEmpty()) {
            return@withContext CheckResult(
                category = CheckCategory.COMPETITOR,
                score = 0,
                maxScore = MAX_SCORE,
                issues = listOf(
                    Issue(
                        severity = Severity.LOW,
                        code = "COMPETITOR_NO_CONFIG",
                        message = "No competitor packages configured in aso-config.json",
                        suggestion = "Add 'competitorPackages' array to aso-config.json",
                        expectedImpact = "Cannot analyze competitors without package names",
                        estimatedEffort = "5 minutes"
                    )
                )
            )
        }
        
        val primaryCompetitor = competitorPackages.first()
        val analysis = analyzeCompetitor(primaryCompetitor)
        
        if (analysis != null) {
            val score = calculateRatingScore(analysis.rating, issues) +
                       calculateInstallsScore(analysis.installsNumeric, issues) +
                       calculateKeywordsScore(analysis.keywords, issues) +
                       calculateFreshnessScore(analysis.lastUpdated, issues)
            
            CheckResult(
                category = CheckCategory.COMPETITOR,
                score = score,
                maxScore = MAX_SCORE,
                issues = issues
            )
        } else {
            issues.add(
                Issue(
                    severity = Severity.MEDIUM,
                    code = "COMPETITOR_FETCH_FAILED",
                    message = "Failed to fetch competitor data for $primaryCompetitor",
                    suggestion = "Check internet connection and package name validity",
                    expectedImpact = "Missing competitor benchmark data",
                    estimatedEffort = "10 minutes"
                )
            )
            CheckResult(
                category = CheckCategory.COMPETITOR,
                score = 0,
                maxScore = MAX_SCORE,
                issues = issues
            )
        }
    }

    private suspend fun analyzeCompetitor(packageName: String): CompetitorAnalysis? {
        cache.get(packageName)?.let { return it }
        
        val result = scraper.scrapeAppDetails(packageName)
        return when {
            result.isSuccess -> {
                val analysis = result.getOrThrow()
                cache.put(packageName, analysis)
                analysis
            }
            else -> null
        }
    }

    private fun calculateRatingScore(rating: Float, issues: MutableList<Issue>): Int {
        return when {
            rating >= 4.5f -> 6
            rating >= MIN_RATING -> {
                issues.add(
                    Issue(
                        severity = Severity.LOW,
                        code = "COMPETITOR_RATING_BELOW_EXCELLENT",
                        message = "Competitor rating is $rating (below 4.5)",
                        suggestion = "Analyze competitor reviews to understand strengths",
                        expectedImpact = "Benchmark for quality standards",
                        estimatedEffort = "15 minutes"
                    )
                )
                4
            }
            else -> {
                issues.add(
                    Issue(
                        severity = Severity.MEDIUM,
                        code = "COMPETITOR_RATING_LOW",
                        message = "Competitor rating is $rating (below $MIN_RATING minimum)",
                        suggestion = "This may indicate market opportunity",
                        expectedImpact = "Lower quality benchmark",
                        estimatedEffort = "N/A"
                    )
                )
                2
            }
        }
    }

    private fun calculateInstallsScore(installs: Long?, issues: MutableList<Issue>): Int {
        return when {
            installs == null -> {
                issues.add(
                    Issue(
                        severity = Severity.LOW,
                        code = "COMPETITOR_INSTALLS_UNKNOWN",
                        message = "Could not parse competitor install count",
                        suggestion = "Manual review of competitor popularity",
                        expectedImpact = "Missing popularity benchmark",
                        estimatedEffort = "5 minutes"
                    )
                )
                3
            }
            installs >= MIN_INSTALLS * 10 -> 6
            installs >= MIN_INSTALLS -> {
                issues.add(
                    Issue(
                        severity = Severity.LOW,
                        code = "COMPETITOR_INSTALLS_MODERATE",
                        message = "Competitor has $installs installs (good but not exceptional)",
                        suggestion = "Analyze competitor's marketing strategy",
                        expectedImpact = "Realistic popularity benchmark",
                        estimatedEffort = "30 minutes"
                    )
                )
                4
            }
            else -> {
                issues.add(
                    Issue(
                        severity = Severity.MEDIUM,
                        code = "COMPETITOR_INSTALLS_LOW",
                        message = "Competitor has only $installs installs (below $MIN_INSTALLS minimum)",
                        suggestion = "This may be a niche competitor",
                        expectedImpact = "Limited market penetration benchmark",
                        estimatedEffort = "N/A"
                    )
                )
                2
            }
        }
    }

    private fun calculateKeywordsScore(keywords: List<String>, issues: MutableList<Issue>): Int {
        return if (keywords.size >= MIN_KEYWORDS_MATCH) {
            4
        } else {
            issues.add(
                Issue(
                    severity = Severity.LOW,
                    code = "COMPETITOR_KEYWORDS_LIMITED",
                    message = "Competitor has only ${keywords.size} identifiable keywords",
                    suggestion = "Manual review of competitor's keyword strategy",
                    expectedImpact = "Limited keyword benchmarking",
                    estimatedEffort = "10 minutes"
                )
            )
            2
        }
    }

    private fun calculateFreshnessScore(lastUpdated: kotlinx.datetime.Instant, issues: MutableList<Issue>): Int {
        val now = kotlinx.datetime.Clock.System.now()
        val daysSinceUpdate = (now - lastUpdated).inWholeDays
        
        return when {
            daysSinceUpdate <= 30 -> 4
            daysSinceUpdate <= 90 -> {
                issues.add(
                    Issue(
                        severity = Severity.LOW,
                        code = "COMPETITOR_UPDATE_STALE",
                        message = "Competitor last updated $daysSinceUpdate days ago",
                        suggestion = "Monitor for competitor updates",
                        expectedImpact = "Potentially outdated benchmark",
                        estimatedEffort = "5 minutes"
                    )
                )
                3
            }
            else -> {
                issues.add(
                    Issue(
                        severity = Severity.MEDIUM,
                        code = "COMPETITOR_UPDATE_VERY_STALE",
                        message = "Competitor last updated $daysSinceUpdate days ago (very stale)",
                        suggestion = "This competitor may be inactive",
                        expectedImpact = "Outdated benchmark",
                        estimatedEffort = "N/A"
                    )
                )
                1
            }
        }
    }

    private fun extractCompetitorPackages(configText: String): List<String> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val map = json.decodeFromString<Map<String, Any>>(configText)
            @Suppress("UNCHECKED_CAST")
            (map["competitorPackages"] as? List<String>) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
