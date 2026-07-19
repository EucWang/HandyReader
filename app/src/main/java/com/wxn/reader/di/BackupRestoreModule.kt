package com.wxn.reader.di

import com.wxn.reader.data.backup.BackupExporter
import com.wxn.reader.data.backup.BackupImporter
import com.wxn.reader.data.backup.ContentHashCalculator
import com.wxn.reader.data.backup.StableIdResolver
import com.wxn.reader.data.source.local.SyncPreferencesUtil
import com.wxn.reader.util.sync.BackupProgressEmitter
import com.wxn.reader.util.sync.BackupRestoreManager
import com.wxn.reader.util.sync.HybridLogicalClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * ★ v1.4 建议-F2:必须用 `object` + `@Provides`(全项目 0 处 @Binds,对齐 AppModule/NetworkModule 模式)。
 *
 * ★ 同步方案 §10.2。
 */
@Module
@InstallIn(SingletonComponent::class)
object BackupRestoreModule {

    // HybridLogicalClock / BackupExporter / BackupImporter / ContentHashCalculator / StableIdResolver /
    // SyncRecordMapper / SyncMergeEngine 均用 @Inject constructor,Hilt 自动构建,无需 @Provides。

    @Provides
    @Singleton
    fun provideBackupProgressEmitter(): BackupProgressEmitter = BackupProgressEmitter()

    @Provides
    @Singleton
    fun provideBackupRestoreManager(
        exporter: BackupExporter,
        importer: BackupImporter,
        hlc: HybridLogicalClock,
        emitter: BackupProgressEmitter,
        syncPrefs: SyncPreferencesUtil,
    ): BackupRestoreManager = BackupRestoreManager(exporter, importer, hlc, emitter, syncPrefs)
}
