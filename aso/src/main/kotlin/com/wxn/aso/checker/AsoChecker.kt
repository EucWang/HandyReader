package com.wxn.aso.checker

import com.wxn.aso.model.AsoReport
import com.wxn.aso.model.CheckCategory
import com.wxn.aso.model.ReportMetadata
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import java.io.File

/**
 * ASO检查器
 * 纯JVM实现，不继承ViewModel，使用coroutineScope替代viewModelScope
 */
class AsoChecker(
    private val projectRoot: File,
    private val metadataCheck: MetadataCheck,
    private val visualAssetsCheck: VisualAssetsCheck,
    private val localizationCheck: LocalizationCheck,
    private val keywordsCheck: KeywordsCheck,
    private val competitorCheck: CompetitorCheck
) {

    suspend fun runAllChecks(): AsoReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 使用coroutineScope替代viewModelScope
        val results = coroutineScope {
            listOf(
                async { metadataCheck.check() },
                async { visualAssetsCheck.check() },
                async { localizationCheck.check() },
                async { keywordsCheck.check() },
                async { competitorCheck.check() }
            ).awaitAll()
        }

        val durationMs = System.currentTimeMillis() - startTime
        val overallScore = results.sumOf { it.score }
        val maxScore = results.sumOf { it.maxScore }

        AsoReport(
            id = generateReportId(),
            timestamp = Clock.System.now(),
            overallScore = overallScore,
            categoryResults = results,
            metadata = ReportMetadata(
                appVersion = "1.9.260312", // TODO: get from manifest
                checkVersion = "1.0.0",
                durationMs = durationMs,
                checksPerformed = results.size
            )
        )
    }

    /**
     * 快速检查模式（仅元数据和关键词检查）
     */
    suspend fun runQuickChecks(): AsoReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 使用coroutineScope替代viewModelScope
        val results = coroutineScope {
            listOf(
                async { metadataCheck.check() },
                async { keywordsCheck.check() }
            ).awaitAll()
        }

        val durationMs = System.currentTimeMillis() - startTime
        val overallScore = results.sumOf { it.score }
        val maxScore = results.sumOf { it.maxScore }

        AsoReport(
            id = generateReportId(),
            timestamp = Clock.System.now(),
            overallScore = overallScore,
            categoryResults = results,
            metadata = ReportMetadata(
                appVersion = "1.9.260312", // TODO: get from manifest
                checkVersion = "1.0.0",
                durationMs = durationMs,
                checksPerformed = results.size
            )
        )
    }

    private fun generateReportId(): String {
        return "aso_${System.currentTimeMillis()}"
    }
}
