package com.wxn.reader.di

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.room.Room
import com.wxn.base.util.Logger
import com.wxn.bookparser.TextParser
import com.wxn.bookread.data.model.preference.FirstHintPrefsUtil
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
import com.wxn.bookread.data.source.local.TranslatorPrefsUtil
import com.wxn.bookread.data.source.local.TtsPreferencesUtil
import com.wxn.reader.data.mapper.annotation.BookAnnotationMapper
import com.wxn.reader.data.mapper.annotation.BookAnnotationMapperImpl
import com.wxn.reader.data.mapper.book.BookMapper
import com.wxn.reader.data.mapper.book.BookMapperImpl
import com.wxn.reader.data.mapper.book.ChapterMapper
import com.wxn.reader.data.mapper.book.ChapterMapperImpl
import com.wxn.reader.data.mapper.bookmark.BookmarkMapper
import com.wxn.reader.data.mapper.bookmark.BookmarkMapperImpl
import com.wxn.reader.data.mapper.bookshelf.BookShelfMapper
import com.wxn.reader.data.mapper.bookshelf.BookShelfMapperImpl
import com.wxn.reader.data.mapper.note.NoteMapper
import com.wxn.reader.data.mapper.note.NoteMapperImpl
import com.wxn.reader.data.mapper.readbg.ReadBgMapper
import com.wxn.reader.data.mapper.readbg.ReadBgMapperImpl
import com.wxn.reader.data.mapper.readingactive.ReadingActiveMapper
import com.wxn.reader.data.mapper.readingactive.ReadingActiveMapperImpl
import com.wxn.reader.data.mapper.shelf.ShelfMapper
import com.wxn.reader.data.mapper.shelf.ShelfMapperImpl
import com.wxn.reader.data.mapper.sherpamodel.SherpaModelMapper
import com.wxn.reader.data.mapper.sherpamodel.SherpaModelMapperImpl
import com.wxn.reader.data.remote.api.ReadBgsApi
import com.wxn.reader.data.remote.api.TTSModelsApi
import com.wxn.reader.data.remote.api.TranslateApi
import com.wxn.reader.data.remote.api.DictionaryApi
import com.wxn.reader.data.remote.opds.OpdsApiClient
import com.wxn.reader.data.repository.BooksRepositoryImpl
import com.wxn.reader.data.repository.ChaptersRepositoryImpl
import com.wxn.reader.data.repository.DownloadRepositoryImpl
import com.wxn.reader.data.repository.FontRepositoryImpl
import com.wxn.reader.data.repository.PermissionRepositoryImpl
import com.wxn.reader.data.repository.ReadBgRepositoryImpl
import com.wxn.reader.data.repository.ShelfRepositoryImpl
import com.wxn.reader.data.repository.TTSModelsRepositoryImpl
import com.wxn.reader.data.repository.TranslateRepositoryImpl
import com.wxn.reader.data.repository.DictionaryRepositoryImpl
import com.wxn.reader.data.repository.OpdsRepositoryImpl
import com.wxn.reader.data.repository.VocabularyRepositoryImpl
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.AnalysisPrefUtil
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.data.source.local.BatteryOptimazePrefsUtil
import com.wxn.reader.data.source.local.DictionaryPrefsUtil
import com.wxn.reader.data.source.local.ThemePreferencesUtil
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.BookVocabularyDao
import com.wxn.reader.data.source.local.dao.DictionaryCacheDao
import com.wxn.reader.data.source.local.dao.DownloadHistoryDao
import com.wxn.reader.data.source.local.dao.FontDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadBgDao
import com.wxn.reader.data.source.local.dao.ReaderThemeConfigDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.data.source.local.dao.SherpaModelDao
import com.wxn.reader.data.source.local.dao.SherpaSpeakerDao
import com.wxn.reader.data.source.local.OpdsBlacklistStore
import com.wxn.reader.data.source.local.OpdsCredentialStore
import com.wxn.reader.data.source.local.dao.DeletedBookDao
import com.wxn.reader.data.source.local.dao.OpdsBookMappingDao
import com.wxn.reader.data.source.local.dao.OpdsCatalogDao
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.domain.repository.ChaptersRepository
import com.wxn.reader.domain.repository.DownloadRepository
import com.wxn.reader.domain.repository.FontRepository
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.reader.domain.repository.ReadBgRepository
import com.wxn.reader.domain.repository.ShelfRepository
import com.wxn.reader.domain.repository.TTSModelsRepository
import com.wxn.reader.domain.repository.TranslateRepository
import com.wxn.reader.domain.repository.DictionaryRepository
import com.wxn.reader.domain.repository.OpdsRepository
import com.wxn.reader.domain.repository.VocabularyRepository
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.bookmarks.GetBookmarksForBookUseCase
import com.wxn.reader.domain.use_case.books.UpdateProgressFieldsUseCase
import com.wxn.reader.domain.use_case.books.UpdateWordCountUseCase
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.UpdateChapterWordCountUserCase
import com.wxn.reader.domain.use_case.notes.GetNotesForBookUseCase
import com.wxn.reader.presentation.mainReader.PageViewController
import com.wxn.reader.service.TtsStateHolder
import com.wxn.reader.util.BookChineseConverter
import com.wxn.reader.util.PdfBitmapConverter
import com.wxn.reader.util.TtsServiceController
import com.wxn.reader.util.download.OKHttpStringStreamer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class) //live as long as our application
object AppModule {

    private const val DATABASE_NAME = "uread_database"

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideBookMapper(chineseConverter: BookChineseConverter): BookMapper {
        return BookMapperImpl(chineseConverter)
    }

    @Provides
    @Singleton
    fun provideChapterMapper(): ChapterMapper {
        return ChapterMapperImpl()
    }

    @Provides
    @Singleton
    fun provideAnnotationMapper(): BookAnnotationMapper {
        return BookAnnotationMapperImpl()
    }

    @Provides
    @Singleton
    fun provideBookmarkMapper(): BookmarkMapper {
        return BookmarkMapperImpl()
    }

    @Provides
    @Singleton
    fun provideBookshelfMapper(): BookShelfMapper {
        return BookShelfMapperImpl()
    }

    @Provides
    @Singleton
    fun provideNoteMapper(): NoteMapper {
        return NoteMapperImpl()
    }

    @Provides
    @Singleton
    fun provideReadingActiveMapper(): ReadingActiveMapper {
        return ReadingActiveMapperImpl()
    }

    @Provides
    @Singleton
    fun provideShelfMapper(): ShelfMapper {
        return ShelfMapperImpl()
    }

    @Provides
    @Singleton
    fun provideReadBgMapper(): ReadBgMapper {
        return ReadBgMapperImpl()
    }

//    @Provides
//    @Singleton
//    fun provideAppDatabase(@ApplicationContext appContext: Context): AppDatabase {
////        return Room.databaseBuilder(
////            appContext,
////            AppDatabase::class.java,
////            "book_database"
////        )
//////            .addMigrations(AppDatabase.MIGRATION_1_2) // Add your migration here
////            .build()
//
//        return Room.inMemoryDatabaseBuilder(appContext, AppDatabase::class.java).build()
//    }

    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        deviceLocalStore: com.wxn.reader.data.source.local.DeviceLocalStore,
    ): AppDatabase {
        try {
            val field = android.database.CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 10 * 1024 * 1024)
        } catch (_: Exception) {
        }
        // ★ v9 同步方案:Migration_8_9 需要本机 deviceId 回填 reading_activities/uuid 等
        val localDeviceId = deviceLocalStore.getOrCreateLocalDeviceId()
        Log.i("AppModule", "provideAppDatabase:localDeviceId: $localDeviceId")
        val db = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.Migration_2_3)
            .addMigrations(AppDatabase.Migration_3_4)
            .addMigrations(AppDatabase.Migration_4_5)
            .addMigrations(AppDatabase.Migration_5_6)
            .addMigrations(AppDatabase.Migration_6_7)
            // ★ A+++ 严重-5:合并 v7→v8→v9→v10 为单一 Migration(本期未发布,v7 是线上版本)
            .addMigrations(AppDatabase.createMigration7To10(localDeviceId))
            // ★ v11 per-book 阅读配置(reader_theme_configs 加对齐两列 + 两张 per_book 新表)
            .addMigrations(AppDatabase.Migration_10_11)
            .build()

        // ★ v12 post-migration 清理：删除孤儿 .idx 影子缓存(详见 plan-txt-unify-byte-offset.md §6.2)。
        // PR1 落地后所有 TXT 读取路径改走 DB，.idx 立即变为孤儿文件。清理不影响正确性，只影响整洁度。
        // 用 SharedPreferences flag 守护一次性执行（避免每次启动重复 syscall）。
        // 时序安全：Room migration 在首次 DAO 查询时惰性执行，不在 build() 时执行；
        // .idx 删除与 DB schema 变更独立，无时序依赖。
        val prefs = context.getSharedPreferences("db_post_migration", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("txt_idx_orphans_cleaned_v12", false)) {
            runCatching {
                java.io.File(context.cacheDir, "txtindex").deleteRecursively()
            }
            // runCatching：清理失败（IO 异常）不影响 app 启动。deleteRecursively() 对不存在目录是 no-op，
            // fresh install 也安全。每个 .idx 仅几 KB，残留孤儿无功能影响。
            prefs.edit().putBoolean("txt_idx_orphans_cleaned_v12", true).apply()
        }

        return db
    }

    @Provides
    @Singleton
    fun provideBookDao(appDatabase: AppDatabase): BookDao {
        return appDatabase.bookDao()
    }

    @Provides
    @Singleton
    fun provideDownloadHistoryDao(appDatabase: AppDatabase): DownloadHistoryDao {
        return appDatabase.downloadHistoryDao()
    }

    @Provides
    @Singleton
    fun provideReadBgDao(appDatabase: AppDatabase): ReadBgDao {
        return appDatabase.readBgDao()
    }

    @Provides
    @Singleton
    fun provideReaderThemeConfigDao(appDatabase: AppDatabase): ReaderThemeConfigDao {
        return appDatabase.readerThemeConfigDao()
    }

    // ===== ★ v11 per-book 阅读配置 DAO + Repository(见设计方案 §Step 1.6/1.8)=====
    @Provides
    @Singleton
    fun providePerBookMetaDao(appDatabase: AppDatabase): com.wxn.reader.data.source.local.dao.PerBookMetaDao {
        return appDatabase.perBookMetaDao()
    }

    @Provides
    @Singleton
    fun providePerBookThemeOverrideDao(appDatabase: AppDatabase): com.wxn.reader.data.source.local.dao.PerBookThemeOverrideDao {
        return appDatabase.perBookThemeOverrideDao()
    }

    // PerBookConfigRepository 通过 @Inject constructor 自动绑定（无需 @Provides，否则 DuplicateBindings）


    @Provides
    @Singleton
    fun provideChapterDao(appDatabase: AppDatabase): ChapterDao {
        return appDatabase.bookChapterDao()
    }

    // ===== ★ v12 TXT 统一字节偏移方案（plan-txt-unify-byte-offset.md §10.1）=====
    // 桥接 bookparser 的 TxtBookMetaStore 接口到 app 模块的 Room DAO 实现。
    // TxtTextParser @Inject constructor 依赖 TxtBookMetaStore（接口），由 Hilt 解析到此 provider。
    @Provides
    @Singleton
    fun provideTxtBookMetaStore(
        bookDao: BookDao,
        chapterDao: ChapterDao,
    ): com.wxn.bookparser.parser.txt.TxtBookMetaStore {
        return com.wxn.reader.data.repository.TxtBookMetaStoreImpl(bookDao, chapterDao)
    }


    @Provides
    @Singleton
    fun provideAnnotationDao(appDatabase: AppDatabase): AnnotationDao {
        return appDatabase.annotationDao()
    }

    @Provides
    @Singleton
    fun provideNoteDao(appDatabase: AppDatabase): NoteDao {
        return appDatabase.noteDao()
    }

    @Provides
    @Singleton
    fun provideBookmarkDao(appDatabase: AppDatabase): BookmarkDao {
        return appDatabase.bookmarkDao()
    }

    @Provides
    @Singleton
    fun provideShelfDao(appDatabase: AppDatabase): ShelfDao {
        return appDatabase.shelfDao()
    }

    @Provides
    @Singleton
    fun provideBookShelfDao(appDatabase: AppDatabase): BookShelfDao {
        return appDatabase.bookShelfDao()
    }

    @Provides
    @Singleton
    fun provideReadingActivityDao(appDatabase: AppDatabase): ReadingActivityDao {
        return appDatabase.readingActivityDao()
    }

    // ===== ★ v9 同步方案新增 DAO(一期建表,sync_queue/sync_etag_cache 不写入不读取)=====
    @Provides
    @Singleton
    fun provideSyncQueueDao(appDatabase: AppDatabase): com.wxn.reader.data.source.local.dao.SyncQueueDao {
        return appDatabase.syncQueueDao()
    }

    @Provides
    @Singleton
    fun provideSyncEtagCacheDao(appDatabase: AppDatabase): com.wxn.reader.data.source.local.dao.SyncEtagCacheDao {
        return appDatabase.syncEtagCacheDao()
    }

    @Provides
    @Singleton
    fun provideBookReadingTimeDao(appDatabase: AppDatabase): com.wxn.reader.data.source.local.dao.BookReadingTimeDao {
        return appDatabase.bookReadingTimeDao()
    }

    @Provides
    @Singleton
    fun provideAppPreferencesUtil(@ApplicationContext context: Context): AppPreferencesUtil {
        return AppPreferencesUtil(context)
    }

    @Provides
    @Singleton
    fun provideThemePreferencesUtil(@ApplicationContext context: Context): ThemePreferencesUtil {
        return ThemePreferencesUtil(context)
    }

    @Provides
    @Singleton
    fun provideBatteryOptimazePrefsUtil(@ApplicationContext context: Context): BatteryOptimazePrefsUtil {
        return BatteryOptimazePrefsUtil(context)
    }

    @Provides
    @Singleton
    fun provideDictionaryPrefsUtil(@ApplicationContext context: Context): DictionaryPrefsUtil {
        return DictionaryPrefsUtil(context)
    }

    @Provides
    @Singleton
    fun provideAnalysisPrefUtil(@ApplicationContext context: Context): AnalysisPrefUtil {
        return AnalysisPrefUtil(context)
    }

    @Provides
    @Singleton
    fun provideReviewPrefsUtil(@ApplicationContext context: Context): com.wxn.reader.data.source.local.ReviewPrefsUtil {
        return com.wxn.reader.data.source.local.ReviewPrefsUtil(context)
    }

    @Provides
    @Singleton
    fun provideCrashPrefs(@ApplicationContext context: Context): com.wxn.reader.data.source.local.CrashPrefs {
        return com.wxn.reader.data.source.local.CrashPrefs(context)
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun providePageViewController(
        @ApplicationContext context: Context,
        getChapterByIdUserCase: GetChapterByIdUserCase,
        getChapterCountByBookIdUserCase: GetChapterCountByBookIdUserCase,

        getAnnotationsUseCase: GetAnnotationsUseCase,
        getNotesForBookUseCase: GetNotesForBookUseCase,
        getBookmarksForBookUseCase : GetBookmarksForBookUseCase,

        updateChapterWordCountUserCase: UpdateChapterWordCountUserCase,
        updateProgressFieldsUseCase: UpdateProgressFieldsUseCase,
        updateWordCountUseCase: UpdateWordCountUseCase,

        appPreferencesUtil: AppPreferencesUtil,

        textParser: TextParser,
        ttsStateHolder: TtsStateHolder,
        ttsServiceController: TtsServiceController,
    ): PageViewController {
        return PageViewController(
            context,
            getChapterByIdUserCase,
            getChapterCountByBookIdUserCase,
            getAnnotationsUseCase,
            getNotesForBookUseCase,
            getBookmarksForBookUseCase,
            updateChapterWordCountUserCase,
            updateProgressFieldsUseCase,
            updateWordCountUseCase,

            appPreferencesUtil,
            textParser,
            ttsStateHolder,
            ttsServiceController
        )
    }

    @Provides
    @Singleton
    fun provideTtsStateHolder(preferencesUtil: TtsPreferencesUtil): TtsStateHolder {
        return TtsStateHolder(preferencesUtil)
    }

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideTtsServiceController(
        @ApplicationContext context: Context,
        ttsStateHolder: TtsStateHolder): TtsServiceController {
        return TtsServiceController(context, ttsStateHolder)
    }

    @Provides
    @Singleton
    fun provideChaptersRepository(
        chapterDao: ChapterDao,
        chapterMapper: ChapterMapper
    ): ChaptersRepository {
        return ChaptersRepositoryImpl(
            chapterDao,
            chapterMapper
        )
    }

    @Provides
    @Singleton
    fun provideBooksRepository(
        db: AppDatabase,
        bookDao: BookDao,
        annotationDao: AnnotationDao,
        noteDao: NoteDao,
        bookmarkDao: BookmarkDao,
        readingActivityDao: ReadingActivityDao,
        bookReadingTimeDao: com.wxn.reader.data.source.local.dao.BookReadingTimeDao,
        chapterDao: ChapterDao,
        deviceLocalStore: com.wxn.reader.data.source.local.DeviceLocalStore,
        bookMapper: BookMapper,
        annotationMapper: BookAnnotationMapper,
        bookmarkMapper: BookmarkMapper,
        noteMapper: NoteMapper,
        readingActiveMapper: ReadingActiveMapper,
        shelfMapper: ShelfMapper,
        bookShelfMapper: BookShelfMapper,
        // ★ 同步装饰器依赖
        syncQueueDao: com.wxn.reader.data.source.local.dao.SyncQueueDao,
        syncPrefs: com.wxn.reader.data.source.local.SyncPreferencesUtil,
        hlc: com.wxn.reader.util.sync.HybridLogicalClock,
        stableIdResolver: com.wxn.reader.data.backup.StableIdResolver
    ): BooksRepository {
        val impl = BooksRepositoryImpl(
            db,
            bookDao,
            annotationDao,
            noteDao,
            bookmarkDao,
            readingActivityDao,
            bookReadingTimeDao,
            chapterDao,
            deviceLocalStore,
            bookMapper,
            annotationMapper,
            bookmarkMapper,
            noteMapper,
            readingActiveMapper,
            shelfMapper,
            bookShelfMapper
        )
        // ★ 同步装饰器:始终注入,内部 HLC-only 短路(isSyncEnabled 恒 false → 不写 sync_queue)
        return com.wxn.reader.data.repository.SyncableBooksRepository(
            impl, bookDao, annotationDao, noteDao, bookmarkDao,
            syncQueueDao, syncPrefs, hlc, stableIdResolver
        )
    }

    @Provides
    @Singleton
    fun provideShelfRepository(
        shelfDao: ShelfDao,
        bookShelfDao: BookShelfDao,
        bookDao: BookDao,
        shelfMapper: ShelfMapper,
        bookMapper: BookMapper,
        bookShelfMapper: BookShelfMapper,
        // ★ 同步装饰器依赖
        syncQueueDao: com.wxn.reader.data.source.local.dao.SyncQueueDao,
        syncPrefs: com.wxn.reader.data.source.local.SyncPreferencesUtil,
        hlc: com.wxn.reader.util.sync.HybridLogicalClock
    ): ShelfRepository {
        val impl = ShelfRepositoryImpl(
            shelfDao,
            bookShelfDao,
            bookDao,
            shelfMapper,
            bookMapper,
            bookShelfMapper
        )
        // ★ 同步装饰器:HLC-only 短路
        return com.wxn.reader.data.repository.SyncableShelfRepository(
            impl, shelfDao, bookShelfDao, syncQueueDao, syncPrefs, hlc
        )
    }

//    @Provides
//    @Singleton
//    fun provideHttpClient(): HttpClient {
//        return DefaultHttpClient()
//    }
//
//    @Provides
//    @Singleton
//    fun provideAssetRetriever(
//        @ApplicationContext context: Context,
//        httpClient: HttpClient
//    ): AssetRetriever {
//        return AssetRetriever(context.contentResolver, httpClient)
//    }
//
//    @Provides
//    @Singleton
//    fun providePublicationParser(
//        @ApplicationContext context: Context,
//        httpClient: HttpClient,
//        assetRetriever: AssetRetriever
//    ): DefaultPublicationParser {
//        return DefaultPublicationParser(context, httpClient, assetRetriever, null)
//    }
//
//    @Provides
//    @Singleton
//    fun providePublicationOpener(publicationParser: DefaultPublicationParser): PublicationOpener {
//        return PublicationOpener(publicationParser)
//    }


    @Provides
    @Singleton
    fun providePdfBitmapConverter(@ApplicationContext context: Context): PdfBitmapConverter {
        return PdfBitmapConverter(context)
    }


    @Provides
    @Singleton
    fun providePermissionRepository(application: Application): PermissionRepository =
        PermissionRepositoryImpl(application)

    @Provides
    @Singleton
    fun provideReaderPreferences(@ApplicationContext context: Context): ReaderPreferencesUtil {
        return ReaderPreferencesUtil(context)
    }

    @Provides
    @Singleton
    fun provideTranslatorPrefUtil(@ApplicationContext context: Context): TranslatorPrefsUtil {
        return TranslatorPrefsUtil(context)
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


    @Provides
    @Singleton
    fun provideFirstHintPrefsUtil(@ApplicationContext context: Context) : FirstHintPrefsUtil {
        return FirstHintPrefsUtil(context)
    }

    @Provides
    @Singleton
    fun provideReadBgRepository(
        readBgsApi: ReadBgsApi,
        dao: ReadBgDao,
        readBgMapper: ReadBgMapper
    ) : ReadBgRepository {
        return ReadBgRepositoryImpl(readBgsApi, dao, readBgMapper)
    }


    @Provides
    @Singleton
    fun provideDownloadRepository(
        downloadHistoryDao: DownloadHistoryDao
    ): DownloadRepository {
        return DownloadRepositoryImpl(
            downloadHistoryDao
        )
    }


    @Provides
    @Singleton
    fun provideSherpaModelMapper(): SherpaModelMapper {
        return SherpaModelMapperImpl()
    }


    @Provides
    @Singleton
    fun provideSherpaModelDao(appDatabase: AppDatabase): SherpaModelDao {
        return appDatabase.sherpaModelDao()
    }

    @Provides
    @Singleton
    fun provideSherpaSpeakerDao(appDatabase: AppDatabase): SherpaSpeakerDao {
        return appDatabase.sherpaSpeakerDao()
    }

    @Provides
    @Singleton
    fun provideFontDao(appDatabase: AppDatabase): FontDao {
        return appDatabase.fontDao()
    }

    @Provides
    @Singleton
    fun provideFontRepository(fontDao: FontDao,
                              appDatabase: AppDatabase,
                              @ApplicationContext context: Context,
                              okHttpStringStreamer: OKHttpStringStreamer): FontRepository {
        return FontRepositoryImpl(fontDao, appDatabase, context, okHttpStringStreamer)
    }

    @Provides
    @Singleton
    fun provideTTSModelsRepository(
        ttsModelsApi: TTSModelsApi,
        sherpaModelDao: SherpaModelDao,
        sherpaSpeakerDao: SherpaSpeakerDao,
        sherpaModelMapper: SherpaModelMapper,
        appdatabase: AppDatabase,
        okHttpStringStreamer: OKHttpStringStreamer
    ): TTSModelsRepository {
        return TTSModelsRepositoryImpl(
            ttsModelsApi,
            sherpaModelDao,
            sherpaSpeakerDao,
            sherpaModelMapper,
            appdatabase,
            okHttpStringStreamer
        )
    }

    @Provides
    @Singleton
    fun provideTranslateRepository(
        translateApi: TranslateApi,
        @ApplicationContext context: Context,
        json: Json
    ): TranslateRepository {
        return TranslateRepositoryImpl(translateApi, context, json)
    }

    @Provides
    @Singleton
    fun provideDictionaryCacheDao(appDatabase: AppDatabase): DictionaryCacheDao {
        return appDatabase.dictionaryCacheDao()
    }

    @Provides
    @Singleton
    fun provideDictionaryRepository(
        dictionaryApi: DictionaryApi,
        dictionaryCacheDao: DictionaryCacheDao,
        json: Json,
        dictionaryPrefsUtil: DictionaryPrefsUtil
    ): DictionaryRepository {
        return DictionaryRepositoryImpl(dictionaryApi, dictionaryCacheDao, json, dictionaryPrefsUtil)
    }

    @Provides
    @Singleton
    fun provideBookVocabularyDao(appDatabase: AppDatabase): BookVocabularyDao {
        return appDatabase.bookVocabularyDao()
    }

    @Provides
    @Singleton
    fun provideVocabularyRepository(
        vocabularyDao: BookVocabularyDao,
        // ★ 同步装饰器依赖
        syncQueueDao: com.wxn.reader.data.source.local.dao.SyncQueueDao,
        syncPrefs: com.wxn.reader.data.source.local.SyncPreferencesUtil,
        hlc: com.wxn.reader.util.sync.HybridLogicalClock
    ): VocabularyRepository {
        val impl = VocabularyRepositoryImpl(vocabularyDao)
        // ★ 同步装饰器:HLC-only 短路
        return com.wxn.reader.data.repository.SyncableVocabularyRepository(
            impl, vocabularyDao, syncQueueDao, syncPrefs, hlc
        )
    }

    @Provides
    @Singleton
    fun provideOpdsCredentialStore(@ApplicationContext context: Context): OpdsCredentialStore {
        return OpdsCredentialStore(context)
    }

    @Provides
    @Singleton
    fun provideOpdsBlacklistStore(@ApplicationContext context: Context): OpdsBlacklistStore {
        return OpdsBlacklistStore(context)
    }

    @Provides
    @Singleton
    fun provideOpdsApiClient(
        @ApplicationContext context: Context,
        @Named("DownloadOkHttpClient") okHttpClient: OkHttpClient,
        credentialStore: OpdsCredentialStore
    ): OpdsApiClient {
        return OpdsApiClient(context, okHttpClient, credentialStore)
    }

    @Provides
    @Singleton
    fun provideOpdsCatalogDao(appDatabase: AppDatabase): OpdsCatalogDao {
        return appDatabase.opdsCatalogDao()
    }

    @Provides
    @Singleton
    fun provideOpdsBookMappingDao(appDatabase: AppDatabase): OpdsBookMappingDao {
        return appDatabase.opdsBookMappingDao()
    }

    @Provides
    @Singleton
    fun provideDeletedBookDao(appDatabase: AppDatabase): DeletedBookDao {
        return appDatabase.deletedBookDao()
    }

    @Provides
    @Singleton
    fun provideOpdsRepository(
        opdsCatalogDao: OpdsCatalogDao,
        opdsApiClient: OpdsApiClient,
        credentialStore: OpdsCredentialStore,
        blacklistStore: OpdsBlacklistStore
    ): OpdsRepository {
        return OpdsRepositoryImpl(opdsCatalogDao, opdsApiClient, credentialStore, blacklistStore)
    }
}