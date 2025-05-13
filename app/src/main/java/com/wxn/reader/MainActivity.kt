package com.wxn.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.wxn.base.ui.BaseActivity
//import com.google.android.gms.ads.MobileAds
import com.wxn.reader.data.model.AppLanguage
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.PurchaseHelperController
import com.wxn.reader.ui.theme.ReadTheme
import com.wxn.reader.navigation.SetupNavGraph
import com.wxn.reader.util.LanguageHelper
import com.wxn.reader.util.PurchaseHelper
import com.wxn.reader.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : BaseActivity() {


    val viewModel: SplashViewModel by viewModels()

    // experimental
    private val languageHelper = LanguageHelper()


    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val splashScreen = installSplashScreen()
        enableEdgeToEdge()

        val initialLanguage = AppLanguage.fromCode(AppPreferencesUtil.defaultPreferences.language)
        languageHelper.updateBaseContextLocale(this, initialLanguage)

        // Keep splash screen visible until loading is complete
        splashScreen.setKeepOnScreenCondition {
            viewModel.isLoading.value
        }

        // Initialize billing first
        val purchaseHelper = PurchaseHelper(this)
//        purchaseHelper.billingSetup() //TODO 更新vip状态

        // Initialize ads in background
//        if (BuildConfig.ENABLE_AD) {
//            CoroutineScope(Dispatchers.IO).launch {
//                MobileAds.initialize(this@MainActivity)
//            }
//        }


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