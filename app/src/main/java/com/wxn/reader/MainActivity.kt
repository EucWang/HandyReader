package com.wxn.reader

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.wxn.base.ui.BaseActivity
import com.wxn.base.util.Logger
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.events.VolumeEventBus
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.navigation.SetupNavGraph
import com.wxn.reader.presentation.home.states.ImportProgressState
import com.wxn.reader.ui.theme.ReadTheme
import com.wxn.reader.util.PurchaseHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class MainActivity : BaseActivity() {

    val viewModel: SplashViewModel by viewModels()

    @Inject
    lateinit var readerPreferencesUtil: ReaderPreferencesUtil

    @Inject
    lateinit var readTipPreferencesUtil: ReadTipPreferencesUtil

    @Inject
    lateinit var appPreferencesUtil: AppPreferencesUtil

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        // Keep splash screen visible until loading is complete
        splashScreen.setKeepOnScreenCondition {
            viewModel.isLoading.value
        }

        // Initialize billing first
        val purchaseHelper = PurchaseHelper(this)
//        purchaseHelper.billingSetup()

        // Initialize ads in background
//        if (BuildConfig.ENABLE_AD) {
//            CoroutineScope(Dispatchers.IO).launch {
//                MobileAds.initialize(this@MainActivity)
//            }
//        }
        ChapterProvider.init(this, readTipPreferencesUtil, readerPreferencesUtil)

        setContent {
            val screen by viewModel.startDestination.collectAsStateWithLifecycle()

            val navController = rememberNavController()

            CompositionLocalProvider(LocalNavController provides navController,
                PurchaseHelperController provides purchaseHelper) {
                ReadTheme {
                    screen?.let {
                        SetupNavGraph(
                            startDestination = it,
                        )
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        lifecycleScope.launch(
            Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
                Logger.e(throwable) }
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> VolumeEventBus.emitVolumeUp()
                KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeEventBus.emitVolumeDown()
            }
        }
        Logger.d("MainActivity::onKeyDown keyCode:$keyCode, inReadPage;$inReadPage")
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN -> inReadPage
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        var inReadPage = false
    }
}