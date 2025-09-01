package com.wxn.reader

import android.app.Activity
import android.app.Application
import android.content.res.Configuration
import android.os.Bundle
import com.wxn.base.util.Coroutines
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.util.LanguageUtil
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BookApplication : Application() {

    companion object {
        lateinit var app: BookApplication
    }

    @Inject
    lateinit var appPrefs : AppPreferencesUtil

    private var topActivity: Activity? = null

    override fun onCreate() {
        super.onCreate()
        app = this

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks{
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?
            ) {
                Logger.d("BookApplication::onActivityCreated::${activity.javaClass.name}")
                Coroutines.scope().launch {
                    appPrefs.appPrefsFlow.firstOrNull()?.language?.let { lang ->
                        LanguageUtil.changeLanguage(activity, lang)
                    }
                }
            }

            override fun onActivityDestroyed(activity: Activity) {
                Logger.d("BookApplication::onActivityDestroyed::${activity.javaClass.name}")
            }

            override fun onActivityPaused(activity: Activity) {
                Logger.d("BookApplication::onActivityPaused::${activity.javaClass.name}")
            }

            override fun onActivityResumed(activity: Activity) {
                Logger.d("BookApplication::onActivityResumed::${activity.javaClass.name}")
                topActivity = activity
            }

            override fun onActivitySaveInstanceState(
                activity: Activity,
                outState: Bundle
            ) {
                Logger.d("BookApplication::onActivitySaveInstanceState::${activity.javaClass.name}")
            }

            override fun onActivityStarted(activity: Activity) {
                Logger.d("BookApplication::onActivityStarted::${activity.javaClass.name}")
            }

            override fun onActivityStopped(activity: Activity) {
                Logger.d("BookApplication::onActivityStopped::${activity.javaClass.name}")
            }
        })
        initComponent()
    }

    private fun initComponent() {
        LanguageUtil.initDefaultLanguage(this)
        Logger.init(BuildConfig.DEBUG)
        ToastUtil.init(this)
    }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        Coroutines.scope().launch {
            appPrefs.appPrefsFlow.firstOrNull()?.language?.let { lang ->
                LanguageUtil.changeLanguage(this@BookApplication, lang)
            }
        }
    }

    fun onLanguageChange() {
        Logger.d("BookApplication::onLanguageChange::topActivity[$topActivity]")
        topActivity?.recreate()
    }

}