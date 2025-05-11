package com.wxn.reader.presentation.home


import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.file.CachedFileBuilder
import com.wxn.bookparser.domain.file.CachedFileCompat
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.data.dto.ReadingStatus
import com.wxn.reader.data.dto.ReadingStatus.Companion.intToReadStatus

import com.wxn.reader.data.model.SortOption
import com.wxn.reader.data.model.SortOrder
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.domain.use_case.books.DeleteBookByUriUseCase
import com.wxn.reader.domain.use_case.books.DeleteBookUseCase
import com.wxn.reader.domain.use_case.books.GetBookUrisUseCase
import com.wxn.reader.domain.use_case.books.GetBooksUseCase
import com.wxn.reader.domain.use_case.books.InsertBookUseCase
import com.wxn.reader.domain.use_case.books.UpdateBookUseCase
import com.wxn.reader.domain.use_case.permission.GrantPersistableUriPermission
import com.wxn.reader.domain.use_case.shelves.AddBookToShelfUseCase
import com.wxn.reader.domain.use_case.shelves.AddShelfUseCase
import com.wxn.reader.domain.use_case.shelves.GetBooksForShelfUseCase
import com.wxn.reader.domain.use_case.shelves.GetShelvesUseCase
import com.wxn.reader.domain.use_case.shelves.RemoveBooksFromShelfUseCase
import com.wxn.reader.domain.use_case.shelves.RemoveShelfUseCase
import com.wxn.reader.presentation.home.states.ImportProgressState
import com.wxn.reader.presentation.home.states.SnackbarState
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.ImageUtils
import com.wxn.reader.util.Logger
import com.wxn.reader.util.PurchaseHelper
import com.wxn.reader.util.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.ErrorException
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.toAbsoluteUrl
import org.readium.r2.streamer.PublicationOpener
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import com.wxn.bookparser.exts.*
import com.wxn.bookparser.supportedExtensions

@HiltViewModel
class HomeViewModel
@Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val getBookUrisUseCase: GetBookUrisUseCase,
    private val insertBookUseCase: InsertBookUseCase,
    private val updateBookUseCase: UpdateBookUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val deleteBookByUriUseCase: DeleteBookByUriUseCase,
    private val addShelfUseCase: AddShelfUseCase,
    private val removeShelfUseCase: RemoveShelfUseCase,
    private val getShelvesUseCase: GetShelvesUseCase,
    private val addBookToShelfUseCase: AddBookToShelfUseCase,
    private val removeBooksFromShelfUseCase: RemoveBooksFromShelfUseCase,
    private val getBooksForShelfUseCase: GetBooksForShelfUseCase,
    private val assetRetriever: AssetRetriever,
    private val publicationOpener: PublicationOpener,
    private val appPreferencesUtil: AppPreferencesUtil,
    private val fileParser: FileParser,
    application: Application,
) : AndroidViewModel(application) {

    private val context: Context
        get() = getApplication<Application>().applicationContext

    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()


    private val _appPreferences = MutableStateFlow(AppPreferencesUtil.defaultPreferences)
    val appPreferences: StateFlow<AppPreferences> = _appPreferences.asStateFlow()


    private val _isAddingBooks = MutableStateFlow(false)
    val isAddingBooks: StateFlow<Boolean> = _isAddingBooks.asStateFlow()

    private val _books = MutableStateFlow<PagingData<Book>>(PagingData.empty())
    val books: StateFlow<PagingData<Book>> = _books.asStateFlow()

    private val _selectedBooks = MutableStateFlow<List<Book>>(emptyList())
    val selectedBooks: StateFlow<List<Book>> = _selectedBooks.asStateFlow()

    private val _selectionMode = MutableStateFlow(false)
    val selectionMode: StateFlow<Boolean> = _selectionMode.asStateFlow()


    private val _booksInShelfSet = MutableStateFlow<Set<Long>>(emptySet())
    val booksInShelfSet: StateFlow<Set<Long>> = _booksInShelfSet.asStateFlow()

    private val _currentShelf = MutableStateFlow<Shelf?>(null)
    private val currentShelf: StateFlow<Shelf?> = _currentShelf.asStateFlow()


    private val _selectedTabRow = MutableStateFlow(0)
    val selectedTabRow: StateFlow<Int> = _selectedTabRow.asStateFlow()

    var selectedTab = mutableStateOf(0)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()


    private var refreshJob: Job? = null

    private val _importProgressState = MutableStateFlow<ImportProgressState>(ImportProgressState.Idle)
    val importProgressState: StateFlow<ImportProgressState> = _importProgressState.asStateFlow()

    private val _snackbarState = MutableStateFlow<SnackbarState>(SnackbarState.Hidden)
    val snackbarState: StateFlow<SnackbarState> = _snackbarState.asStateFlow()

    private var snackbarJob: Job? = null

    var showLayoutModal = mutableStateOf(false)
    var showSortModal = mutableStateOf(false)
    var showMetadataModal = mutableStateOf(false)

    init {
        initializeApp()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            val preferences = appPreferencesUtil.appPreferencesFlow.first()
            coroutineScope {
                launch { loadBooks(preferences) }
                launch { loadShelves() }
                launch { observeBooks(preferences) }
                launch { observeAppPreferences() }
                if (preferences.scanDirectories.isEmpty()) {
                    launch { addPublicDomainBooksIfNeeded() }
                }
            }
        }
    }

    private suspend fun addPublicDomainBooksIfNeeded() {
        val publicDomainBooks = listOf("alice_in_wonderlands.epub", "romeo_and_juliet.epub")
        val existingUris = getBookUrisUseCase().toSet()
        publicDomainBooks.forEach { fileName ->
            val internalFile = copyAssetToInternalStorage(fileName)
            val internalUri = Uri.fromFile(internalFile)
            if (!existingUris.contains(internalUri.toString())) {
                getBookInfoFromInternalFile(internalFile)?.let { book ->
                    insertBookUseCase(book)
                }
            }
        }
//        val initialPreferences = appPreferencesUtil.appPreferencesFlow.first()
//        val updatedAppPreferences = initialPreferences.copy(isAssetsBooksFetched = true)
//        appPreferencesUtil.updateAppPreferences(updatedAppPreferences)
    }

    private fun copyAssetToInternalStorage(fileName: String): File {
        val assetManager = context.assets
        val internalDir = File(context.filesDir, "books")
        if (!internalDir.exists()) internalDir.mkdirs()
        val internalFile = File(internalDir, fileName)
        if (!internalFile.exists()) {
            assetManager.open("books/$fileName").use { input ->
                FileOutputStream(internalFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return internalFile
    }

    private suspend fun getBookInfoFromInternalFile(file: File): Book? {
        val documentFile = DocumentFile.fromFile(file)
        val bookWithCover = fileParser.parse(documentFile)
//        return getBookInfo(documentFile)
        return bookWithCover?.book
    }

    private fun loadBooks(preferences: AppPreferences) {
        val sortBy = preferences.sortBy
        val sortOrder = preferences.sortOrder
        val readingStatus = preferences.readingStatus
        val fileType = preferences.fileTypes
        val isAscending = sortOrder == SortOrder.ASCENDING

        viewModelScope.launch {
            combine(
//                getBooksUseCase(sortBy, isAscending, readingStatus, fileType).cachedIn(
//                    viewModelScope
//                ),
                getBooksUseCase.getSortedBooks(sortBy, isAscending, readingStatus, fileType),
                searchQuery,
                currentShelf,
                booksInShelfSet,
                selectedTabRow
            ) { books, query, shelf, shelfBookIds, selectedTabRow ->
                books.filter { book ->
//                    Logger.d("HomeViewModel:loadBooks:${book}")
                    val matchesSearch =
                        query.isBlank() || book.title.contains(query, ignoreCase = true)
                    val matchesShelf = shelf == null || book.id in shelfBookIds
                    val matchesTab = if (selectedTabRow == 1) {
                        stringToFileType(book.fileType) == FileType.AUDIOBOOK
                    } else {
                        stringToFileType(book.fileType) != FileType.AUDIOBOOK
                    }
                    matchesSearch && matchesShelf && matchesTab
                }
            }.collect { data ->
//                Logger.d("HomeViewModel:loadBooks:${data.size}")
                _books.value =  PagingData.from(data)
//                _books.value = filteredPagingData
            }
        }
    }

    private fun loadShelves() {
        viewModelScope.launch {
            getShelvesUseCase().collect { shelves ->
                _shelves.value = shelves
            }
        }
    }

    fun updateCurrentShelf(shelf: Shelf?) {
        _currentShelf.value = shelf
    }


    fun updateCurrentTabRow(tab: Int) {
        _selectedTabRow.value = tab
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun observeAppPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.appPreferencesFlow.collect { preferences ->
                _appPreferences.value = preferences
                // Optionally reload books if sort preferences change
                loadBooks(preferences)
            }
        }
    }

    fun refreshBooks() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(500)
            showSnackbar("Refreshing Library" )
            val scanDirectory = appPreferences.value.scanDirectories
            if (scanDirectory.isNotEmpty()) {
                observeBooks(appPreferences.value)
            } else {
                showSnackbar("No directory set for scanning books" )
            }
        }
    }

    private fun showSnackbar(
        message: String,
        unlimited: Boolean = false,
    ) {
        snackbarJob?.cancel()
        snackbarJob = viewModelScope.launch {
            _snackbarState.value = SnackbarState.Visible(
                message = message,
                unlimited = unlimited,
            )

            if(!unlimited) {
                delay(3000)
                hideSnackbar()
            }
        }


    }

    private fun hideSnackbar() {
        snackbarJob?.cancel()
        _snackbarState.value = SnackbarState.Hidden
    }

    private fun observeBooks(preferences: AppPreferences) {
        viewModelScope.launch(Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Logger.e(throwable)
            _importProgressState.value =
                ImportProgressState.Error(throwable.message ?: "Unknown error occurred")
            _isAddingBooks.value = false
            showSnackbar(
                message = "Error during import: ${throwable.message}",
            )
        }) {
            try {
                //已经存到数据库中的书籍
                val existingUris = getBookUrisUseCase().toSet()

                //从用户目录中导入的书籍文件列表
                val documentFiles = mutableListOf<DocumentFile>()
                preferences.scanDirectories.forEach { directoryPath ->
                    Logger.d("HomeViewModel::observeBooks::directoryPath=$directoryPath")
                    val uri = Uri.parse(directoryPath)
                    val filesInDirectory = getBooksFromDirectory(context, uri)
                    Logger.d("HomeViewModel::observeBooks::filesInDirectory=${filesInDirectory.size},")
                    documentFiles.addAll(filesInDirectory)
                }
                //从文件列表中的文件去重复
                val uniqueFiles = documentFiles.distinctBy { it.uri.toString() } //去重复

                //不在数据库中，但是在用户的搜索目录中的文件，就是用户新增加的文件
                val newBooks = uniqueFiles.filter { documentFile ->
                    val bookUriString = documentFile.uri.toString()
                        !existingUris.contains(bookUriString)            //不在数据库中
                }

                //当前目录中的文件对应的uri
                val currentUris = uniqueFiles.map { it.uri.toString() }.toSet()

                //将资产目录中的2个预置书籍放入到存储目录中
                val assetBookUris = listOf("alice_in_wonderlands.epub", "romeo_and_juliet.epub").map {
                    Uri.fromFile(copyAssetToInternalStorage(it)).toString()
                }

//                Logger.d("HomeViewModel::observeBooks::assetBookUris=${assetBookUris},existingUris=${existingUris}")
                //在数据库中， 但是不在用户的扫描目录中的uri，则是用户已经删除掉了的书籍
                val deletedUris = existingUris.filter { it !in currentUris && it !in assetBookUris }

                Logger.d("HomeViewModel::observeBooks::newBooks.size=${newBooks.size}")
                if (newBooks.isNotEmpty()) {        //有新增加的，则将新增加的加入到数据库中
                    _isAddingBooks.value = true
                    _importProgressState.value = ImportProgressState.InProgress(0, newBooks.size)
                    showSnackbar(
                        message = stringResource(R.string.adding_new_book_to_library)
                    )
//
//                    // Process books in smaller batches
                    newBooks.chunked(5).forEachIndexed { batchIndex, batch ->
                        // Add delay between batches to prevent overwhelming the system
                        if (batchIndex > 0) delay(100)

                        batch.forEachIndexed { index, documentFile ->
                            try {
                                val totalProcessed = (batchIndex * 5) + index + 1
                                _importProgressState.value =
                                    ImportProgressState.InProgress(totalProcessed, newBooks.size)

                                // Update snackbar with progress
                                showSnackbar(
                                    message = stringResource(R.string.adding_books_num, totalProcessed, newBooks.size),
                                    unlimited = true
                                )

                                // Check if book already exists before adding
//                                val bookUriString = documentFile.uri.toString()
//                                if (!getBookUrisUseCase().toSet().contains(bookUriString)) {
                                    addNewBook(documentFile)
//                                } else {
//                                    Logger.d("HomeViewModel::${bookUriString} already exists.")
//                                }
                            } catch (e: Exception) {
                                Logger.e("HomeViewModel::Error adding book: ${documentFile.name}, ${e.message}")
                            }
                        }
                    }

                    // Process books in smaller batches
//                    newBooks.forEachIndexed { index, documentFile ->
//                        try {
//                            val totalProcessed = index + 1
//                            _importProgressState.value =
//                                ImportProgressState.InProgress(totalProcessed, newBooks.size)
//
//                            // Update snackbar with progress
//                            showSnackbar(
//                                message = stringResource(R.string.adding_books_num, totalProcessed, newBooks.size),
//                                unlimited = true
//                            )
//
//                            // Check if book already exists before adding
//                            val bookUriString = documentFile.uri.toString()
//                            if (!getBookUrisUseCase().toSet().contains(bookUriString)) {
//                                addNewBook(documentFile)
//                            } else {
//                                Logger.d("HomeViewModel::${bookUriString} already exists.")
//                            }
//                        } catch (e: Exception) {
//                            Logger.e("HomeViewModel::Error adding book: ${documentFile.name}, ${e.message}")
//                        }
//                    }


                    _importProgressState.value = ImportProgressState.Complete
                    showSnackbar(
                        message = stringResource(R.string.added_books, newBooks.size)
                    )
                    _isAddingBooks.value = false
                }

                // Handle deleted books in batches
                Logger.d("HomeViewModel::observeBooks::deletedUris.size=${deletedUris.size}")
                if (deletedUris.isNotEmpty()) {
                    showSnackbar(
                        message = stringResource(R.string.remove_nums_books, deletedUris.size)
                    )
                    deletedUris.chunked(10).forEach { batch ->
                        batch.forEach { bookUri ->
                            try {
                                deleteBookByUriUseCase(bookUri)
                            } catch (e: Exception) {
                                Logger.e("HomeViewModel::Error deleting book: $bookUri,${e.message}")
                            }
                        }
                        delay(50)
                    }
                    showSnackbar(
                        message = stringResource(R.string.remove_nums_books, deletedUris.size)
                    )
                }

                loadBooks(appPreferences.value)
            } catch (e: Exception) {
                _importProgressState.value = ImportProgressState.Error(e.message ?: "Unknown error occurred")
                Logger.e("HomeViewModel::Error observing books:${e.message}")
                showSnackbar(
                    message = stringResource(R.string.error_updateing_library, e.message ?: "Unknown error occurred")
                )
            } finally {
                _isAddingBooks.value = false
//                val initialPreferences = appPreferencesUtil.appPreferencesFlow.first()
//                val updatedAppPreferences = initialPreferences.copy(isAssetsBooksFetched = true)
//                appPreferencesUtil.updateAppPreferences(updatedAppPreferences)
            }
        }
    }

    fun updateAppPreferences(newPreferences: AppPreferences) {
        viewModelScope.launch {
            Logger.d("HomeViewModel::updateAppPreferences:the home viewModel")
            appPreferencesUtil.updateAppPreferences(newPreferences)
        }
    }

    fun resetLayoutPreferences() {
        viewModelScope.launch {
            appPreferencesUtil.resetLayoutPreferences()
        }
    }

    fun addShelf(shelfName: String) {
        viewModelScope.launch {
            try {
                val currentShelves = _shelves.value
                val newOrder = currentShelves.size
                val newShelfId = addShelfUseCase(shelfName, newOrder)
                _shelves.value += Shelf(id = newShelfId, name = shelfName, order = newOrder)
            } catch (e: Exception) {
//                showSnackbar("Failed to add shelf: ${e.message}" )
                showSnackbar(stringResource(R.string.failed_to_add_shelf, e.message.orEmpty()))
            }
        }
    }

    fun removeShelf(shelf: Shelf) {
        viewModelScope.launch {
            try {
                removeShelfUseCase(shelf)
                _shelves.value = _shelves.value.filter { it.id != shelf.id }
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.failed_remove_shelf, e.message.orEmpty()))
            }
        }
    }

    fun removeBooks(books: List<Book>, hardRemove: Boolean) {
        viewModelScope.launch {
            try {
                if (hardRemove) {
                    books.forEach { book ->
                        try {
                            val uri = Uri.parse(book.filePath)
                            if (uri.scheme == "content") {
                                // Use ContentResolver to delete the file
                                val contentResolver = context.contentResolver
                                val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                                    uri,
                                    DocumentsContract.getDocumentId(uri)
                                )
                                if (DocumentsContract.deleteDocument(
                                        contentResolver,
                                        documentUri
                                    )
                                ) {
                                    deleteBookUseCase(book)
                                } else {
                                    showSnackbar(stringResource(R.string.failed_delete_book, book.title))
                                }
                            } else {
                                // Handle cases where the URI is not a content URI (e.g., file://)
                                val bookFile = uri.path?.let { File(it) }
                                if (bookFile != null) {
                                    if (bookFile.exists() && bookFile.delete()) {
                                        deleteBookUseCase(book)
                                    } else {
                                        showSnackbar(stringResource(R.string.failed_delete_book, book.title))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            showSnackbar(
                                stringResource(R.string.failed_delete_book_info, book.title, e.message.orEmpty())
                            )
                        }
                    }
                } else {
                    books.map { book ->
                        book.copy(deleted = true)
                    }.forEach {
                        updateBookUseCase(it)
                    }
                }
                showSnackbar(stringResource(R.string.book_remove_success))
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.failed_delete_book, e.message.toString()))
            }
        }
    }

    fun addBooksToShelves(bookIds: List<Long>, shelfIds: List<Long>) {
        viewModelScope.launch {
            try {
                bookIds.forEach { bookId ->
                    shelfIds.forEach { shelfId ->
                        addBookToShelfUseCase(bookId, shelfId)
                    }
                }
                showSnackbar(stringResource(R.string.books_add_shelf_success))
            } catch (e: Exception) {
                showSnackbar(stringResource(R.string.add_book_shelf_failed, e.message.toString()))
            }
        }
    }

    fun removeBooksFromShelves(bookIds: List<Long>, shelfIds: List<Long>) {
        viewModelScope.launch {
            try {
                bookIds.forEach { bookId ->
                    shelfIds.forEach { shelfId ->
                        removeBooksFromShelfUseCase(bookId, shelfId)
                    }
                }

                _booksInShelfSet.value -= bookIds.toSet()
//                showSnackbar("Books removed from shelf successfully" )
                showSnackbar(stringResource(R.string.remove_books_from_shelf_success))
            } catch (e: Exception) {
//                showSnackbar("Failed to remove books from shelf: ${e.message}" )
                showSnackbar(stringResource(R.string.remove_books_from_shelf_fail, e.message.toString()))
            }
        }
    }

    fun getBooksForShelfSelection(shelfId: Long): Flow<List<Book>> {
        return getBooksForShelfUseCase(shelfId)
    }

    fun getBooksForShelf(shelfId: Long): List<Book> {
        var booksList: List<Book> = emptyList()
        viewModelScope.launch {
            getBooksForShelfUseCase(shelfId).collect { books: List<Book> ->
                booksList = books
                _booksInShelfSet.value = books.map { it.id }.toSet()
            }
        }
        return booksList
    }

    private suspend fun getBooksFromDirectory(context: Context, uri: Uri): List<DocumentFile> {
        return withContext(Dispatchers.IO) {
            try {
                val documentFile = DocumentFile.fromTreeUri(context, uri)
                documentFile?.let { scanDirectory(it) } ?: emptyList()
            } catch (e: Exception) {
                Logger.e("HomeViewModel::Error scanning directory: $uri, $e")
                emptyList()
            }
        }
    }

    private fun scanDirectory(directory: DocumentFile): List<DocumentFile> {
        return try {
//            val allowedExtensions = listOf("epub", "pdf", "mp3", "m4a", "m4b", "aac").let {
//                if (_appPreferences.value.enablePdfSupport) it else it - "pdf"
//            }
            val allowedExtensions =  supportedExtensions()

            directory.listFiles().filter { file ->
                when {
                    file.isDirectory -> file.name?.let { !it.startsWith(".") } ?: false
//                    file.isFile ->  file.name?.let { name ->
//                        name.substringAfterLast('.', "").lowercase() in allowedExtensions
//                    } ?: false
                    file.isFile ->  file.mimeType in allowedExtensions && file.canRead()  //满足条件 支持的类型 可读

                    else -> false
                }
            }.flatMap { file ->
                if (file.isDirectory) scanDirectory(file) else listOf(file)
            }
        } catch (e: Exception) {
            Logger.e("HomeViewModel:Error scanning directory: ${directory.name}, $e")
            emptyList()
        }
    }

    fun toggleBookSelection(book: Book) {
        _selectedBooks.value = if (_selectedBooks.value.contains(book)) {
            _selectedBooks.value - book
        } else {
            _selectedBooks.value + book
        }
        _selectionMode.value = _selectedBooks.value.isNotEmpty()
    }

    fun selectAllBooks(books: List<Book>){
        _selectedBooks.value = books
    }


    fun clearBookSelection() {
        _selectedBooks.value = emptyList()
        _selectionMode.value = false
    }

    private suspend fun addNewBook(documentFile: DocumentFile) {
        withContext(Dispatchers.IO) {
            try {
//                val bookUriString = documentFile.uri.toString()
//                if (!getBookUrisUseCase().toSet().contains(bookUriString)) {
//                    val book = getBookInfo(documentFile)

                    val cachedFile = CachedFileCompat.fromUri(context,
                        documentFile.uri, CachedFileCompat.build(
                            name = documentFile.name,
                            path = documentFile.uri.path,
                            isDirectory = false
                        ))
//
                    val bookWithCover = fileParser.parse(cachedFile)
//                    val bookWithCover = fileParser.parse(documentFile)
                    bookWithCover?.book?.let {book ->
                        // Add retry mechanism for database operations
                        retry {
                            insertBookUseCase(book)
                        }
                    }
//                }
            } catch (e: Exception) {
                Logger.e("HomeViewModel::Error adding book: ${documentFile.name}, ${e.message}")
                throw e
            }
        }
    }

    /****
     * 如果block运行抛出异常，则尝试attempts次，每次间隔delayBetweenAttempts 毫秒
     * 返回block运行的结果
     */
    private suspend fun <T> retry(
        attempts: Int = 3,
        delayBetweenAttempts: Long = 1000L,
        block: suspend () -> T
    ): T {
        var lastException: Exception? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                if (attempt < attempts - 1) {
                    delay(delayBetweenAttempts)
                }
            }
        }
        throw lastException ?: IllegalStateException("Retry failed")
    }

    private suspend fun getBookInfo(documentFile: DocumentFile): Book {
        var retVal:Book =
            try {
                val url = documentFile.uri.toAbsoluteUrl()
                val fileType: FileType = when {
                    documentFile.name?.endsWith(".pdf", ignoreCase = true) == true -> FileType.PDF
                    documentFile.name?.let {
                        it.endsWith(".mp3", ignoreCase = true) ||
                                it.endsWith(".m4a", ignoreCase = true) ||
                                it.endsWith(".m4b", ignoreCase = true) ||
                                it.endsWith(".aac", ignoreCase = true)
                    } == true -> FileType.AUDIOBOOK

                    else -> FileType.EPUB
                }

               getBookFromType(fileType, url, documentFile)
            } catch (e: Exception) {
                defaultBook(documentFile)
            }
        return retVal
    }

    public class UnknownFileTypeException(val fileType:String) : Exception() {

    }

    private suspend fun getBookFromType(type: FileType, url: AbsoluteUrl?, documentFile: DocumentFile):Book {
        return when (type) {
            FileType.EPUB -> {
                val asset = url?.let { it ->
                    assetRetriever.retrieve(it).getOrElse { throw ErrorException(it) }
                }
                val publication = asset?.let { it ->
                    publicationOpener.open(it, allowUserInteraction = false)
                        .getOrElse { throw ErrorException(it) }
                }
                extractEpubBookInfo(publication, documentFile)
            }

            FileType.PDF -> extractPdfBookInfo(documentFile)
            FileType.AUDIOBOOK -> extractAudioBookInfo(documentFile)
            else -> throw UnknownFileTypeException(type.typeName())
        }
    }

    private fun defaultBook(documentFile: DocumentFile) : Book{
        return Book(
            filePath = documentFile.uri.toString(),
            fileType = when {
                documentFile.name?.endsWith(
                    ".pdf",
                    ignoreCase = true
                ) == true -> FileType.PDF.typeName()

                documentFile.name?.let {
                    it.endsWith(".mp3", ignoreCase = true) ||
                            it.endsWith(".m4a", ignoreCase = true) ||
                            it.endsWith(".m4b", ignoreCase = true) ||
                            it.endsWith(".aac", ignoreCase = true)
                } == true -> FileType.AUDIOBOOK.typeName()

                else -> FileType.EPUB.typeName()
            },
            title = documentFile.name ?: "Unknown",
            author = "",
            description = null,
            publishDate = null,
            publisher = null,
            language = null,
            numberOfPages = null,
            category = "",
            coverImage = null,
            locator = "",
            scrollIndex = 0,
            scrollOffset = 0,
            progress = 0f,
            lastOpened = 0,
        )
    }

    private suspend fun extractAudioBookInfo(documentFile: DocumentFile): Book =
        withContext(Dispatchers.IO) {
            val uri = documentFile.uri
            val mediaMetadataRetriever = MediaMetadataRetriever()

            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    mediaMetadataRetriever.setDataSource(descriptor.fileDescriptor)

                    val title =
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?: documentFile.name ?: "Unknown"
                    val artist =
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val album =
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val duration =
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()

                    // Extract cover art
                    val coverArt = mediaMetadataRetriever.embeddedPicture
                    val coverPath = coverArt?.let {
                        ImageUtils.saveCoverImage(
                            BitmapFactory.decodeByteArray(
                                it,
                                0,
                                it.size
                            ),
                            documentFile.uri.toString(),
                            context
                        )
                    }

                    Book(
                        filePath = uri.toString(),
                        fileType = FileType.AUDIOBOOK.typeName(),
                        title = title,
                        author = artist ?: "",
                        description = album,
                        publishDate = null,
                        publisher = null,
                        language = null,
                        numberOfPages = null,
                        category = "",
                        coverImage = coverPath,
                        locator = "",
                        duration = duration,
                        narrator = artist,
                        scrollIndex = 0,
                        scrollOffset = 0,
                        progress = 0f,
                        lastOpened = 0,
                    )
                } ?: throw IllegalStateException("Unable to open audio file")
            } catch (e: Exception) {
                Book(
                    filePath = uri.toString(),
                    fileType = FileType.AUDIOBOOK.typeName(),
                    title = documentFile.name ?: "Unknown",
                    author = "",
                    description = null,
                    publishDate = null,
                    publisher = null,
                    language = null,
                    numberOfPages = null,
                    category = "",
                    coverImage = null,
                    locator = "",
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    lastOpened = 0,
                )
            } finally {
                mediaMetadataRetriever.release()
            }
        }


    private suspend fun extractPdfBookInfo(documentFile: DocumentFile): Book =
        withContext(Dispatchers.IO) {
            val uri = documentFile.uri
            var pdfRenderer: PdfRenderer? = null

            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    pdfRenderer = PdfRenderer(descriptor)

                    val pageCount = pdfRenderer?.pageCount ?: 0
                    val firstPage = pdfRenderer?.openPage(0)

                    // Extract basic info
                    val title = documentFile.name ?: "Unknown"

                    // Generate and save cover image
                    val coverBitmap = firstPage?.let { page ->
                        val bitmap =
                            Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                    val coverPath = coverBitmap?.let { ImageUtils.saveCoverImage( it, documentFile.uri.toString(), context) }

                    firstPage?.close()

                    Book(
                        filePath = uri.toString(),
                        fileType = FileType.PDF.typeName(),
                        title = title,
                        author = "",
                        description = null,
                        publishDate = null,
                        publisher = null,
                        language = null,
                        numberOfPages = pageCount,
                        category = "",
                        coverImage = coverPath,
                        locator = "",
                        scrollIndex = 0,
                        scrollOffset = 0,
                        progress = 0f,
                        lastOpened = 0,
                    )
                } ?: throw IllegalStateException("Unable to open PDF file")
            } catch (e: Exception) {
                // Log the error or handle it as needed
                Book(
                    filePath = uri.toString(),
                    fileType = FileType.PDF.typeName(),
                    title = documentFile.name ?: "Unknown",
                    author = "",
                    description = null,
                    publishDate = null,
                    publisher = null,
                    language = null,
                    numberOfPages = null,
                    category = "",
                    coverImage = null,
                    locator = "",
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    lastOpened = 0,
                )
            } finally {
                pdfRenderer?.close()
            }
        }


    private suspend fun extractEpubBookInfo(
        publication: Publication?,
        documentFile: DocumentFile
    ): Book {
        val coverBitmap = publication?.cover()
        val coverPath = coverBitmap?.let { ImageUtils.saveCoverImage( it, documentFile.uri.toString(), context) }

        return Book(
            filePath = documentFile.uri.toString(),
            fileType = FileType.EPUB.typeName(),
            title = publication?.metadata?.title ?: documentFile.name ?: "Unknown",
            author = publication?.metadata?.authors?.joinToString(", ") { it.name } ?: "",
            description = publication?.metadata?.description,
            publishDate = publication?.metadata?.published?.toString(),
            publisher = publication?.metadata?.publishers?.firstOrNull()?.name,
            language = publication?.metadata?.languages?.firstOrNull(),
            numberOfPages = publication?.metadata?.numberOfPages,
            category = (publication?.metadata?.subjects?.joinToString(", ") { it.name }.orEmpty()),
            coverImage = coverPath,
            locator = "",
            scrollIndex = 0,
            scrollOffset = 0,
            progress = 0f,
            lastOpened = 0,
        )
    }



    fun updateBook(updatedBook: Book, updatedReadingStatus: Boolean = false) {
        viewModelScope.launch {
            var updateBook: Book = updatedBook
            if (updatedReadingStatus) {
                updateBook = when (intToReadStatus(updatedBook.readingStatus)) {
                    ReadingStatus.NOT_STARTED -> updatedBook.copy(
                        startReadingDate = null,
                        endReadingDate = null,
                        readingTime = 0,
                        progress = 0f
                    )

                    ReadingStatus.IN_PROGRESS -> updatedBook.copy(
                        startReadingDate = System.currentTimeMillis(),
                        endReadingDate = null,
                        readingTime = 0,
                        progress = 0f
                    )

                    ReadingStatus.FINISHED -> updatedBook.copy(
                        endReadingDate = System.currentTimeMillis(),
                        progress = 100f
                    )

                    else -> updatedBook
                }
            }

            updateBookUseCase(updateBook)
        }
    }


    fun sortBooks(sortOption: SortOption, sortOrder: SortOrder) {
        viewModelScope.launch {
            val isAscending = sortOrder == SortOrder.ASCENDING
            val readingStatus = _appPreferences.value.readingStatus
            val fileType = _appPreferences.value.fileTypes
            try {
                getBooksUseCase(sortOption, isAscending, readingStatus, fileType)
                    .cachedIn(viewModelScope)
                    .collect { pagingData ->
                        _books.value = pagingData
                    }
            } catch (e: Exception) {
                Logger.e("HomeViewModel:Error sorting books: ${e.message}")
            }
        }
    }


    fun filterBooks(option: Any) {
        viewModelScope.launch {
            val currentPreferences = _appPreferences.value
            val newPreferences = when (option) {
                is ReadingStatus -> {
                    val newStatuses = if (option in currentPreferences.readingStatus) {
                        currentPreferences.readingStatus - option
                    } else {
                        currentPreferences.readingStatus + option
                    }
                    currentPreferences.copy(readingStatus = newStatuses)
                }

                is FileType -> {
                    val newFileTypes = if (option in currentPreferences.fileTypes) {
                        emptySet()  // Deselect if it's the only selected option
                    } else {
                        setOf(option)  // Select only this option
                    }
                    currentPreferences.copy(fileTypes = newFileTypes)
                }

                else -> currentPreferences
            }

            updateAppPreferences(newPreferences)
            _appPreferences.value = newPreferences

            getBooksUseCase(
                sortOption = newPreferences.sortBy,
                isAscending = newPreferences.sortOrder == SortOrder.ASCENDING,
                readingStatuses = newPreferences.readingStatus,
                fileTypes = newPreferences.fileTypes
            ).collect { pagingData ->
                _books.value = pagingData
            }
        }
    }


    fun purchasePremium(purchaseHelper: PurchaseHelper) {
        purchaseHelper.makePurchase()
        viewModelScope.launch {
            purchaseHelper.isPremium.collect { isPremium ->
                updatePremiumStatus(isPremium)
            }
        }
    }

    fun updatePremiumStatus(isPremium: Boolean) {
        viewModelScope.launch {
            val currentPreferences = appPreferencesUtil.appPreferencesFlow.first()
            if (currentPreferences.isPremium != isPremium) {
                val updatedPreferences = currentPreferences.copy(isPremium = isPremium)
                Logger.d("HomeViewModel:updatePremiumStatus:the home viewModel")
                appPreferencesUtil.updateAppPreferences(updatedPreferences)
                _appPreferences.value = updatedPreferences
            }
        }
    }



}