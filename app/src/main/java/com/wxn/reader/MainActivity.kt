package com.wxn.reader

//import com.google.android.gms.ads.MobileAds
import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.wxn.base.ui.BaseActivity
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.provider.ChapterProvider
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.navigation.SetupNavGraph
import com.wxn.reader.ui.theme.ReadTheme
import com.wxn.reader.util.PurchaseHelper
import dagger.hilt.android.AndroidEntryPoint
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
}