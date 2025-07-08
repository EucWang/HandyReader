package com.wxn.bookread.di

import android.content.Context
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class) //live as long as our application
object ReadModule {
    @Provides
    @Singleton
    fun provideReaderPreferences(@ApplicationContext context: Context): ReaderPreferencesUtil {
        return ReaderPreferencesUtil(context)
    }

    @Provides
    @Singleton
    fun provideReadTipPreferencesUtil(@ApplicationContext context: Context): ReadTipPreferencesUtil {
        return ReadTipPreferencesUtil(context)
    }

    @Provides
    @Singleton
    fun provideTtsPreferencesUtil(@ApplicationContext context: Context) : TtsPreferencesUtil {
        return TtsPreferencesUtil(context)
    }
}