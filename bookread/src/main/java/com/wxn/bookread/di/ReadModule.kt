package com.wxn.bookread.di

import android.content.Context
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class) //live as long as our application
object ReadModule {


//    @Provides
//    @Singleton
//    fun provideContext(@ApplicationContext context: Context): Context {
//        return context
//    }

    @Provides
    @Singleton
    fun provideReaderPreferences(@ApplicationContext context: Context): ReaderPreferencesUtil {
        return ReaderPreferencesUtil(context)
    }
}