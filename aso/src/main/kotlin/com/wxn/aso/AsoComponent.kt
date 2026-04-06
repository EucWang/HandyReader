package com.wxn.aso

import com.wxn.aso.analyzer.ImageAnalyzer
import com.wxn.aso.analyzer.KeywordsExtractor
import com.wxn.aso.api.GooglePlayScraper
import com.wxn.aso.checker.AsoChecker
import com.wxn.aso.checker.CompetitorCheck
import com.wxn.aso.checker.KeywordsCheck
import com.wxn.aso.checker.LocalizationCheck
import com.wxn.aso.checker.MetadataCheck
import com.wxn.aso.checker.VisualAssetsCheck
import com.wxn.aso.data.CompetitorCache
import com.wxn.aso.config.AsoConfig
import com.wxn.aso.config.AsoConfigManager
import com.wxn.aso.config.defaultAsoConfig
import com.wxn.aso.parser.AndroidManifestParser
import com.wxn.aso.parser.StringsXmlParser
import com.wxn.aso.util.AsoLogger
import com.wxn.aso.util.SimpleLogger
import com.wxn.aso.util.ScreenshotSelector
import kotlinx.serialization.json.Json
import java.io.File

/**
 * ASO组件工厂
 * 手动依赖注入，替代Hilt
 */
class AsoComponent(
    private val projectRoot: File
) {
    private val logger: AsoLogger = SimpleLogger()

    private val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val configManager: AsoConfigManager by lazy {
        AsoConfigManager(
            projectRoot = projectRoot,
            json = json,
            logger = logger
        )
    }

    private val stringsXmlParser: StringsXmlParser by lazy {
        StringsXmlParser()
    }

    private val keywordsExtractor: KeywordsExtractor by lazy {
        KeywordsExtractor()
    }

    private val imageAnalyzer: ImageAnalyzer by lazy {
        ImageAnalyzer()
    }

    private val screenshotSelector: ScreenshotSelector by lazy {
        ScreenshotSelector()
    }

    private val androidManifestParser: AndroidManifestParser by lazy {
        AndroidManifestParser()
    }

    private val metadataCheck: MetadataCheck by lazy {
        MetadataCheck(
            projectRoot = projectRoot,
            manifestParser = androidManifestParser
        )
    }

    private val visualAssetsCheck: VisualAssetsCheck by lazy {
        VisualAssetsCheck(
            projectRoot = projectRoot,
            imageAnalyzer = imageAnalyzer,
            screenshotSelector = screenshotSelector
        )
    }

    private val localizationCheck: LocalizationCheck by lazy {
        LocalizationCheck(
            projectRoot = projectRoot,
            stringsXmlParser = stringsXmlParser,
            configManager = configManager
        )
    }

    private val keywordsCheck: KeywordsCheck by lazy {
        KeywordsCheck(
            projectRoot = projectRoot,
            keywordsExtractor = keywordsExtractor,
            configManager = configManager
        )
    }

    private val googlePlayScraper: GooglePlayScraper by lazy {
        GooglePlayScraper()
    }

    private val competitorCache: CompetitorCache by lazy {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "aso_competitor_cache")
        CompetitorCache(cacheDir)
    }

    private val competitorCheck: CompetitorCheck by lazy {
        CompetitorCheck(
            projectRoot = projectRoot,
            scraper = googlePlayScraper,
            cache = competitorCache
        )
    }

    val checker: AsoChecker by lazy {
        AsoChecker(
            projectRoot = projectRoot,
            metadataCheck = metadataCheck,
            visualAssetsCheck = visualAssetsCheck,
            localizationCheck = localizationCheck,
            keywordsCheck = keywordsCheck,
            competitorCheck = competitorCheck
        )
    }

    val config: AsoConfig by lazy {
        // 注意：这是同步获取，实际使用时应该用getConfig()
        defaultAsoConfig
    }

    /**
     * 获取配置（异步版本）
     */
    suspend fun getConfig(): AsoConfig {
        return configManager.getConfig()
    }
}
