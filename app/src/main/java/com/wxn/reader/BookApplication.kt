package com.wxn.reader

import android.app.Application
import com.wxn.reader.util.Logger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        initComponent()
    }

    private fun initComponent() {
        Logger.init()
    }
}