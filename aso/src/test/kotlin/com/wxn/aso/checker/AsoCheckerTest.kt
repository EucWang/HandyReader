package com.wxn.aso.checker

import com.wxn.aso.model.CheckCategory
import com.wxn.aso.model.CheckResult
import com.wxn.aso.model.Issue
import com.wxn.aso.model.Severity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.mockito.kotlin.*

class AsoCheckerTest {

    private val testProjectRoot = File(".").absoluteFile

    @Test
    fun `runAllChecks generates complete report with all categories`() = runTest {
        val mockMetadataCheck = mock<MetadataCheck>()
        val mockVisualAssetsCheck = mock<VisualAssetsCheck>()
        val mockLocalizationCheck = mock<LocalizationCheck>()
        val mockKeywordsCheck = mock<KeywordsCheck>()
        val mockCompetitorCheck = mock<CompetitorCheck>()

        val metadataResult = CheckResult(
            category = CheckCategory.METADATA,
            score = 25,
            maxScore = 30,
            issues = emptyList()
        )
        val visualResult = CheckResult(
            category = CheckCategory.VISUAL,
            score = 30,
            maxScore = 35,
            issues = emptyList()
        )
        val localizationResult = CheckResult(
            category = CheckCategory.LOCALIZATION,
            score = 22,
            maxScore = 25,
            issues = emptyList()
        )
        val keywordsResult = CheckResult(
            category = CheckCategory.KEYWORDS,
            score = 8,
            maxScore = 10,
            issues = emptyList()
        )
        val competitorResult = CheckResult(
            category = CheckCategory.COMPETITOR,
            score = 20,
            maxScore = 20,
            issues = emptyList()
        )

        whenever(mockMetadataCheck.check()).thenReturn(metadataResult)
        whenever(mockVisualAssetsCheck.check()).thenReturn(visualResult)
        whenever(mockLocalizationCheck.check()).thenReturn(localizationResult)
        whenever(mockKeywordsCheck.check()).thenReturn(keywordsResult)
        whenever(mockCompetitorCheck.check()).thenReturn(competitorResult)

        val checker = AsoChecker(
            testProjectRoot,
            mockMetadataCheck,
            mockVisualAssetsCheck,
            mockLocalizationCheck,
            mockKeywordsCheck,
            mockCompetitorCheck
        )

        val report = checker.runAllChecks()

        assertNotNull(report)
        assertNotNull(report.id)
        assertTrue(report.overallScore in 0..120)
        assertEquals(5, report.categoryResults.size)
        assertEquals(105, report.overallScore)
        assertEquals(120, report.categoryResults.sumOf { it.maxScore })
        assertEquals(5, report.metadata.checksPerformed)
        assertTrue(report.metadata.durationMs >= 0)
    }

    @Test
    fun `runAllChecks handles partial failures gracefully`() = runTest {
        val mockMetadataCheck = mock<MetadataCheck>()
        val mockVisualAssetsCheck = mock<VisualAssetsCheck>()
        val mockLocalizationCheck = mock<LocalizationCheck>()
        val mockKeywordsCheck = mock<KeywordsCheck>()
        val mockCompetitorCheck = mock<CompetitorCheck>()

        val metadataResult = CheckResult(
            category = CheckCategory.METADATA,
            score = 30,
            maxScore = 30,
            issues = emptyList()
        )
        val visualResult = CheckResult(
            category = CheckCategory.VISUAL,
            score = 35,
            maxScore = 35,
            issues = emptyList()
        )
        val localizationResult = CheckResult(
            category = CheckCategory.LOCALIZATION,
            score = 15,
            maxScore = 25,
            issues = listOf(Issue(Severity.MEDIUM, "TEST_1", "test", "test", "test", "test"))
        )
        val keywordsResult = CheckResult(
            category = CheckCategory.KEYWORDS,
            score = 5,
            maxScore = 10,
            issues = listOf(
                Issue(Severity.MEDIUM, "TEST_2", "test", "test", "test", "test"),
                Issue(Severity.MEDIUM, "TEST_3", "test", "test", "test", "test")
            )
        )
        val competitorResult = CheckResult(
            category = CheckCategory.COMPETITOR,
            score = 20,
            maxScore = 20,
            issues = emptyList()
        )

        whenever(mockMetadataCheck.check()).thenReturn(metadataResult)
        whenever(mockVisualAssetsCheck.check()).thenReturn(visualResult)
        whenever(mockLocalizationCheck.check()).thenReturn(localizationResult)
        whenever(mockKeywordsCheck.check()).thenReturn(keywordsResult)
        whenever(mockCompetitorCheck.check()).thenReturn(competitorResult)

        val checker = AsoChecker(
            testProjectRoot,
            mockMetadataCheck,
            mockVisualAssetsCheck,
            mockLocalizationCheck,
            mockKeywordsCheck,
            mockCompetitorCheck
        )

        val report = checker.runAllChecks()

        assertEquals(105, report.overallScore)
        assertEquals(2, report.categoryResults.count { it.issues.isNotEmpty() })
    }

    @Test
    fun `runAllChecks includes all category results`() = runTest {
        val mockMetadataCheck = mock<MetadataCheck>()
        val mockVisualAssetsCheck = mock<VisualAssetsCheck>()
        val mockLocalizationCheck = mock<LocalizationCheck>()
        val mockKeywordsCheck = mock<KeywordsCheck>()
        val mockCompetitorCheck = mock<CompetitorCheck>()

        val metadataResult = CheckResult(
            category = CheckCategory.METADATA,
            score = 20,
            maxScore = 30,
            issues = emptyList()
        )
        val visualResult = CheckResult(
            category = CheckCategory.VISUAL,
            score = 25,
            maxScore = 35,
            issues = emptyList()
        )
        val localizationResult = CheckResult(
            category = CheckCategory.LOCALIZATION,
            score = 20,
            maxScore = 25,
            issues = emptyList()
        )
        val keywordsResult = CheckResult(
            category = CheckCategory.KEYWORDS,
            score = 7,
            maxScore = 10,
            issues = emptyList()
        )
        val competitorResult = CheckResult(
            category = CheckCategory.COMPETITOR,
            score = 20,
            maxScore = 20,
            issues = emptyList()
        )

        whenever(mockMetadataCheck.check()).thenReturn(metadataResult)
        whenever(mockVisualAssetsCheck.check()).thenReturn(visualResult)
        whenever(mockLocalizationCheck.check()).thenReturn(localizationResult)
        whenever(mockKeywordsCheck.check()).thenReturn(keywordsResult)
        whenever(mockCompetitorCheck.check()).thenReturn(competitorResult)

        val checker = AsoChecker(
            testProjectRoot,
            mockMetadataCheck,
            mockVisualAssetsCheck,
            mockLocalizationCheck,
            mockKeywordsCheck,
            mockCompetitorCheck
        )

        val report = checker.runAllChecks()

        val categories = report.categoryResults.map { it.category }.toSet()
        assertTrue(categories.contains(CheckCategory.METADATA))
        assertTrue(categories.contains(CheckCategory.VISUAL))
        assertTrue(categories.contains(CheckCategory.LOCALIZATION))
        assertTrue(categories.contains(CheckCategory.KEYWORDS))
        assertEquals(5, categories.size)
    }
}
