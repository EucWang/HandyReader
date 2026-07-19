package com.wxn.reader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.wxn.base.ui.BaseActivity
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.base.util.launchIO
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.bookread.ui.RenderResources
import com.wxn.reader.data.source.local.AnalysisPrefUtil
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.data.source.local.ExternalIntentBridge
import com.wxn.reader.events.VolumeEventBus
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.navigation.SetupNavGraph
import com.wxn.reader.ui.theme.ReadTheme
import com.wxn.reader.util.PurchaseHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
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

    @Inject
    lateinit var analysisPrefUtil: AnalysisPrefUtil

    @Inject
    lateinit var externalIntentBridge: ExternalIntentBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            viewModel.isLoading.value
        }

        val purchaseHelper = PurchaseHelper(this)

        ChapterProvider.init(this, readTipPreferencesUtil, readerPreferencesUtil)
        RenderResources.init(this)

        // 记录首次启动时间戳（幂等，仅首次启动写入一次）
        lifecycleScope.launchIO {
            val isFirstLaunch = appPreferencesUtil.appPrefsFlow.firstOrNull()?.isFirstLaunch ?: true
            analysisPrefUtil.recordFirstLaunchIfNeeded(isFirstLaunch)
        }

        setContent {
            val screen by viewModel.startDestination.collectAsStateWithLifecycle()

            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                launch {
                    externalIntentBridge.navigationRoute.receiveAsFlow().collect { route ->
                        navController.navigate(route) {
                            popUpTo(Screens.HomeScreen.route) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }
                launch {
                    externalIntentBridge.navigationError.receiveAsFlow().collect { message ->
                        ToastUtil.show(message)
                    }
                }
            }

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

        handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    private fun handleExternalIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data ?: intent.clipData?.getItemAt(0)?.uri
            if (uri != null) {
                externalIntentBridge.submit(uri)
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
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> inReadPage && VolumeEventBus.volumeKeyPageTurning
            else -> super.onKeyDown(keyCode, event)
        }
    }

    companion object {
        var inReadPage = false
    }
}
