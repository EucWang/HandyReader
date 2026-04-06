package com.wxn.aso.checker

import com.wxn.aso.api.GooglePlayScraper
import com.wxn.aso.data.CompetitorCache
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompetitorCheckTest {
    @Test
    fun `check returns zero score when no competitor config`() = runTest {
        val mockScraper = mock<GooglePlayScraper>()
        val mockCache = mock<CompetitorCache>()
        val tempDir = createTempDir()
        
        val check = CompetitorCheck(
            projectRoot = tempDir,
            scraper = mockScraper,
            cache = mockCache
        )
        
        val result = check.check()
        
        assertEquals(0, result.score)
        assertEquals(20, result.maxScore)
        assertEquals(1, result.issues.size)
        assertEquals("COMPETITOR_NO_CONFIG", result.issues.first().code)
        
        tempDir.deleteRecursively()
    }
    
    @Test
    fun `check includes competitor packages from config`() = runTest {
        val mockScraper = mock<GooglePlayScraper>()
        val mockCache = mock<CompetitorCache>()
        val tempDir = createTempDir()
        
        val configFile = File(tempDir, "aso-config.json")
        configFile.writeText("""
            {
                "projectRoot": "${tempDir.absolutePath}",
                "competitorPackages": ["com.example.app1", "com.example.app2"]
            }
        """.trimIndent())
        
        val check = CompetitorCheck(
            projectRoot = tempDir,
            scraper = mockScraper,
            cache = mockCache
        )
        
        val result = check.check()
        
        assertTrue(result.issues.any { it.code == "COMPETITOR_FETCH_FAILED" })
        
        tempDir.deleteRecursively()
    }
    
    private fun createTempDir(): File {
        return File(System.getProperty("java.io.tmpdir"), "aso_test_${System.currentTimeMillis()}")
    }
}
