package com.wxn.reader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.wxn.base.util.Logger
import com.wxn.base.util.ToastUtil
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookApplication : Application() {

    companion object {
        lateinit var app: BookApplication
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks{
            override fun onActivityCreated(
                activity: Activity,
                savedInstanceState: Bundle?
            ) {
                Logger.d("BookApplication::onActivityCreated::${activity.javaClass.name}")
            }

            override fun onActivityDestroyed(activity: Activity) {
                Logger.d("BookApplication::onActivityDestroyed::${activity.javaClass.name}")
            }

            override fun onActivityPaused(activity: Activity) {
                Logger.d("BookApplication::onActivityPaused::${activity.javaClass.name}")
            }

            override fun onActivityResumed(activity: Activity) {
                Logger.d("BookApplication::onActivityResumed::${activity.javaClass.name}")
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
        Logger.init(BuildConfig.DEBUG)
        ToastUtil.init(this)
    }
}