package com.wxn.reader.data.source.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardConfig
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardRatio
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteCardStyle
import com.wxn.reader.presentation.shareQuoteCard.model.QuoteFontSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.quoteCardPrefsDataStore by preferencesDataStore(name = "quote_card_prefs")

/**
 * 书摘卡片用户偏好持久化（仿 [ThemePreferencesUtil] 模式）。
 *
 * 持久化上次使用的样式/比例/字号/进度显示开关，下次打开自动恢复。
 * 不持有 Context 引用（DataStore 通过 Context 扩展属性获取）。
 */
@Singleton
class QuoteCardPreferencesUtil @Inject constructor(context: Context) {

    private val dataStore = context.quoteCardPrefsDataStore

    companion object {
        private val STYLE = stringPreferencesKey("quote_card_style")
        private val RATIO = stringPreferencesKey("quote_card_ratio")
        private val FONT_SIZE = stringPreferencesKey("quote_card_font_size")
        private val SHOW_PROGRESS = booleanPreferencesKey("quote_card_show_progress")
    }

    val defaultConfig = QuoteCardConfig()

    init {
        Coroutines.scope().launch {
            initializeDefaultPreferences()
        }
    }

    private suspend fun initializeDefaultPreferences() {
        val preferences = dataStore.data.firstOrNull()
        if (preferences == null) {
            saveConfig(defaultConfig)
        }
    }

    val configFlow: Flow<QuoteCardConfig> = dataStore.data.map { preferences ->
        QuoteCardConfig(
            style = runCatching {
                QuoteCardStyle.valueOf(preferences[STYLE] ?: defaultConfig.style.name)
            }.getOrDefault(defaultConfig.style),
            ratio = runCatching {
                QuoteCardRatio.valueOf(preferences[RATIO] ?: defaultConfig.ratio.name)
            }.getOrDefault(defaultConfig.ratio),
            fontSize = runCatching {
                QuoteFontSize.valueOf(preferences[FONT_SIZE] ?: defaultConfig.fontSize.name)
            }.getOrDefault(defaultConfig.fontSize),
            showProgress = preferences[SHOW_PROGRESS] ?: defaultConfig.showProgress
        )
    }

    /**
     * 保存配置。写入失败仅 log，不阻断流程（运行时 Config 仍有效）。
     */
    suspend fun saveConfig(config: QuoteCardConfig) {
        runCatching {
            dataStore.edit { preferences ->
                preferences[STYLE] = config.style.name
                preferences[RATIO] = config.ratio.name
                preferences[FONT_SIZE] = config.fontSize.name
                preferences[SHOW_PROGRESS] = config.showProgress
            }
        }.onFailure {
            Logger.w("QuoteCardPreferencesUtil saveConfig failed: $it")
        }
    }
}
