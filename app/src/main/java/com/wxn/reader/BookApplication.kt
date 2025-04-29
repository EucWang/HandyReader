package com.wxn.reader

import android.app.Application
import com.wxn.reader.util.Logger
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookApplication : Application() {

    companion object {
        lateinit var app: BookApplication
    }

    override fun onCreate() {
        super.onCreate()
        app = this

        initComponent()
    }

    private fun initComponent() {
        Logger.init()
    }
}