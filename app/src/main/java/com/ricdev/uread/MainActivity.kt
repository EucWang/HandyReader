package com.ricdev.uread

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
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
//import com.google.android.gms.ads.MobileAds
import com.ricdev.uread.data.model.AppLanguage
import com.ricdev.uread.data.source.local.AppPreferencesUtil
import com.ricdev.uread.ui.theme.UReadTheme
import com.ricdev.uread.navigation.SetupNavGraph
import com.ricdev.uread.util.LanguageHelper
import com.ricdev.uread.util.PurchaseHelper
import com.wxn.simplereader2.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {


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
        purchaseHelper.billingSetup()

        // Initialize ads in background
//        if (BuildConfig.ENABLE_AD) {
//            CoroutineScope(Dispatchers.IO).launch {
//                MobileAds.initialize(this@MainActivity)
//            }
//        }


        setContent {
            val screen by viewModel.startDestination.collectAsStateWithLifecycle()


            UReadTheme {
                val navController = rememberNavController()

                screen?.let {
                    SetupNavGraph(
                        navController = navController,
                        startDestination = it,
                        purchaseHelper = purchaseHelper,
                    )
                }
            }
        }
    }
}