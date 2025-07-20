package com.wxn.reader.di

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.wxn.bookparser.TextParser
import com.wxn.bookread.data.source.local.ReadTipPreferencesUtil
import com.wxn.bookread.data.source.local.ReaderPreferencesUtil
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
import com.wxn.reader.data.mapper.readingactive.ReadingActiveMapper
import com.wxn.reader.data.mapper.readingactive.ReadingActiveMapperImpl
import com.wxn.reader.data.mapper.shelf.ShelfMapper
import com.wxn.reader.data.mapper.shelf.ShelfMapperImpl
import com.wxn.reader.data.repository.BooksRepositoryImpl
import com.wxn.reader.data.repository.ChaptersRepositoryImpl
import com.wxn.reader.data.repository.PermissionRepositoryImpl
import com.wxn.reader.data.repository.ShelfRepositoryImpl
import com.wxn.reader.data.source.local.AppDatabase
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.data.source.local.dao.AnnotationDao
import com.wxn.reader.data.source.local.dao.BookDao
import com.wxn.reader.data.source.local.dao.BookShelfDao
import com.wxn.reader.data.source.local.dao.BookmarkDao
import com.wxn.reader.data.source.local.dao.ChapterDao
import com.wxn.reader.data.source.local.dao.NoteDao
import com.wxn.reader.data.source.local.dao.ReadingActivityDao
import com.wxn.reader.data.source.local.dao.ShelfDao
import com.wxn.reader.domain.repository.BooksRepository
import com.wxn.reader.domain.repository.ChaptersRepository
import com.wxn.reader.domain.repository.PermissionRepository
import com.wxn.reader.domain.repository.ShelfRepository
import com.wxn.reader.domain.use_case.annotations.GetAnnotationsUseCase
import com.wxn.reader.domain.use_case.bookmarks.GetBookmarksForBookUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.chapters.GetChapterByIdUserCase
import com.wxn.reader.domain.use_case.chapters.GetChapterCountByBookIdUserCase
import com.wxn.reader.domain.use_case.chapters.UpdateChapterWordCountUserCase
import com.wxn.reader.domain.use_case.notes.GetNotesForBookUseCase
import com.wxn.reader.presentation.mainReader.PageViewController
import com.wxn.reader.util.PdfBitmapConverter
import com.wxn.reader.util.tts.TtsNavigator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
    fun provideBookMapper(): BookMapper {
        return BookMapperImpl()
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(appDatabase: AppDatabase): BookDao {
        return appDatabase.bookDao()
    }


    @Provides
    @Singleton
    fun provideChapterDao(appDatabase: AppDatabase): ChapterDao {
        return appDatabase.bookChapterDao()
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

    @Provides
    @Singleton
    fun provideAppPreferencesUtil(@ApplicationContext context: Context): AppPreferencesUtil {
        return AppPreferencesUtil(context)
    }

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
        updateBookUseCase: UpdateBookUseCase,
        appPreferencesUtil: AppPreferencesUtil,
        textParser: TextParser
    ): PageViewController {
        return PageViewController(
            context,
            getChapterByIdUserCase,
            getChapterCountByBookIdUserCase,
            getAnnotationsUseCase,
            getNotesForBookUseCase,
            getBookmarksForBookUseCase,
            updateChapterWordCountUserCase,
            updateBookUseCase,
            appPreferencesUtil,
            textParser
        )
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
        bookMapper: BookMapper,
        annotationMapper: BookAnnotationMapper,
        bookmarkMapper: BookmarkMapper,
        noteMapper: NoteMapper,
        readingActiveMapper: ReadingActiveMapper,
        shelfMapper: ShelfMapper,
        bookShelfMapper: BookShelfMapper
    ): BooksRepository {
        return BooksRepositoryImpl(
            db,
            bookDao,
            annotationDao,
            noteDao,
            bookmarkDao,
            readingActivityDao,
            bookMapper,
            annotationMapper,
            bookmarkMapper,
            noteMapper,
            readingActiveMapper,
            shelfMapper,
            bookShelfMapper
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
        bookShelfMapper: BookShelfMapper
    ): ShelfRepository {
        return ShelfRepositoryImpl(
            shelfDao,
            bookShelfDao,
            bookDao,
            shelfMapper,
            bookMapper,
            bookShelfMapper
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
    fun provideTtsNavigator(application: Application,
                            ttsPreferencesUtil: TtsPreferencesUtil) : TtsNavigator =
        TtsNavigator(application, ttsPreferencesUtil)

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
//
//@Singleton
//@Provides
//fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
//    return Room.databaseBuilder(context, AppDatabase::class.java, "YourDatabaseName.db")
//        .addCallback(object : RoomDatabase.Callback() {
//            override fun onCreate(db: SupportSQLiteDatabase) {
//                super.onCreate(db)
//                // Check some condition here if needed
//                context.deleteDatabase("YourDatabaseName.db")
//            }
//        })
//        .build()
//}