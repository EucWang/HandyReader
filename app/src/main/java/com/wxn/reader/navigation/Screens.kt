package com.wxn.reader.navigation

import android.net.Uri
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType


fun buildReaderRoute(
    bookId: Long,
    fileType: String,
    filePath: String,
    coverImage: String? = null,
    title: String? = null,
    author: String? = null
): String {
    val encodedUri = Uri.encode(filePath)
    val screen = when (stringToFileType(fileType)) {
        FileType.PDF -> Screens.PdfReaderScreen.route
        FileType.AUDIOBOOK -> Screens.AudiobookReaderScreen.route
        else -> Screens.MainReaderScreen.route
    }
    val base = "$screen/$bookId/$encodedUri"
    // 仅 MainReader 路由拼接 coverImage 段（路由模式 main_book_read_screen/{bookId}/{bookUri}/{coverImage} 要求）
    // PDF/有声书路由模式不含 coverImage 段，不拼接；空值用 "none" 占位避免空段导致路由匹配失败
    return if (screen == Screens.MainReaderScreen.route) {
        val encodedCover = if (coverImage.isNullOrEmpty()) "none" else Uri.encode(coverImage)
        val encodedTitle = if (title.isNullOrEmpty()) "none" else Uri.encode(title)
        val encodedAuthor = if (author.isNullOrEmpty()) "none" else Uri.encode(author)
        "$base/$encodedCover/$encodedTitle/$encodedAuthor"
    } else {
        base
    }
}

sealed class Screens(val route: String) {

    data object GettingStartedScreen : Screens("getting_started_screen")
    data object HomeScreen : Screens("home_screen")
    data object BookReaderScreen: Screens("book_reader_screen")
    data object PdfReaderScreen: Screens("pdf_reader_screen")
    data object AudiobookReaderScreen: Screens("audiobook_reader_screen")
    data object MainReaderScreen: Screens("main_book_read_screen")  //main reader screen

    data object BookDetailsScreen: Screens("book_details_screen")
    data object GeneralSettingsScreen: Screens("general_settings")
    data object ThemeScreen: Screens("theme_screen")
    data object DeletedBooksScreen: Screens("deleted_books_screen")
    data object ShelvesScreen: Screens("shelves_screen")
//    data object AboutAppScreen: Screens("about_app_screen")


    data object NotesScreen: Screens("notes_screen")
    data object StatisticsScreen: Screens("statistics_screen")

    data object PremiumScreen: Screens("premium_screen")

    data object TtsSetScreen: Screens("tts_set_screen")

    data object FeedbackScreen: Screens("feedback_screen")

    data object DownloadHistoryScreen: Screens("download_history_screen")

    data object TTSModelsListPageScreen: Screens("download_tts_models")

    data object FontManagementScreen: Screens("font_management_screen")

    data object LookupHistoryScreen: Screens("lookup_history_screen")

    data object OpdsCatalogListScreen : Screens("opds_catalog_list_screen")
    data object OpdsBrowseScreen : Screens("opds_browse_screen") {
        fun createRoute(catalogId: Long) = "opds_browse_screen/$catalogId"
    }

    /** ★ 同步方案 §7.2:备份与还原入口。 */
    data object BackupSettingsScreen : Screens("backup_settings_screen")
}
