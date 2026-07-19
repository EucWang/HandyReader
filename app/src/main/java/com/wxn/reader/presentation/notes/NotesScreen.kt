package com.wxn.reader.presentation.notes


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.base.bean.Book
import com.wxn.base.ext.toComposeColor
import com.wxn.base.ext.toStringColor
import com.wxn.reader.domain.model.Note
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.bookReader.components.dialogs.NoteContent
import com.wxn.reader.presentation.sharedComponents.AppTopAppBar
import com.wxn.reader.util.SafeScrollableTabRow

/****
 * 已经被替换掉了，这个后续会删除掉
 */
@Deprecated("Deprecated and replace with @NotesScreenV2")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel = hiltViewModel()
) {
    val navController = LocalNavController.current
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()

    val booksWithNotes by viewModel.booksWithNotes.collectAsStateWithLifecycle(initialValue = emptyList())

    val sortedBooksWithNotes = remember(booksWithNotes) {
        booksWithNotes.sortedBy { it.book.title }
    }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(sortedBooksWithNotes.size) {
        if (sortedBooksWithNotes.isEmpty() || selectedTabIndex >= sortedBooksWithNotes.size) {
            selectedTabIndex = 0
        }
    }

    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            searchFocusRequester.requestFocus()
        } else {
            searchQuery = ""
        }
    }

    var showNoteDialog by remember { mutableStateOf(false) }

    var selectedNote by remember {
        mutableStateOf(
            Note(
                note = "",
                selectedText = "",
                color = Color.Yellow.toStringColor(),
                bookId = 0,
                locator = "",
            )
        )
    }

    appPreferences?.let { prefs ->
        Scaffold(
            topBar = {
                AppTopAppBar(
                    title = { Text(stringResource(R.string.notes)) },
                    navigationIcon = {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isSearchActive = !isSearchActive
                            }
                        ) {
                            Icon(
                                if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search Note"
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedVisibility(visible = isSearchActive) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .focusRequester(searchFocusRequester),
                        placeholder = { Text(stringResource(R.string.search_notes)) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    )
                }

                if (sortedBooksWithNotes.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            stringResource(R.string.no_notes_found),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            stringResource(R.string.start_adding_notes_to_your_books),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    val safeTabIndex =
                        selectedTabIndex.coerceIn(0, sortedBooksWithNotes.lastIndex)

                    SafeScrollableTabRow(
                        selectedTabIndex = safeTabIndex,
                        totalTabCount = sortedBooksWithNotes.size,
                        edgePadding = 16.dp
                    ) {
                        sortedBooksWithNotes.forEachIndexed { index, bookWithNotes ->
                            Tab(
                                selected = safeTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = bookWithNotes.book.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }

                    BookNotesContent(
                        appPreferences = prefs,
                        bookWithNotes = sortedBooksWithNotes[safeTabIndex],
                        searchQuery = searchQuery,
                        onNoteClick = { note ->
                            showNoteDialog = true
                            selectedNote = note
                        },
                        onUpdateNote = { updatedNote -> viewModel.updateNote(updatedNote) },
                        onRemoveNote = { note -> viewModel.deleteNote(note) },
                        showPremiumModal = {
                            navController.navigate(Screens.PremiumScreen.route)
                        }
                    )
                }
            }

            if (showNoteDialog) {
                NoteContent(
                    note = selectedNote,
                    onDismiss = { showNoteDialog = false },
                    onEdit = { editedNote ->
                        viewModel.updateNote(editedNote)
                        showNoteDialog = false
                    },
                    onDelete = { note ->
                        viewModel.deleteNote(note)
                        showNoteDialog = false
                    },
                )
            }
        }
    }
}


@Composable
fun BookNotesContent(
    appPreferences: AppPreferences,
    bookWithNotes: BookWithNotes,
    searchQuery: String,
    onNoteClick: (Note) -> Unit,
    onUpdateNote: (Note) -> Unit,
    onRemoveNote: (Note) -> Unit,
    showPremiumModal: () -> Unit
) {
    val filteredNotes = remember(searchQuery, bookWithNotes) {
        bookWithNotes.notes.filter { note ->
            note.note.contains(searchQuery, ignoreCase = true) ||
                    note.selectedText.contains(searchQuery, ignoreCase = true)
        }
    }

    if (filteredNotes.isEmpty() && searchQuery.isNotBlank()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.no_notes_found),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(filteredNotes, key = { it.id }) { note ->
                NoteItem(
                    appPreferences = appPreferences,
                    note = note,
                    onClick = { onNoteClick(note) },
                    onUpdateNote = { updatedNote -> onUpdateNote(updatedNote) },
                    onRemoveNote = { onRemoveNote(note) },
                    showPremiumModal = { showPremiumModal() }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun NoteItem(
    appPreferences: AppPreferences,
    note: Note,
    onClick: () -> Unit,
    onUpdateNote: (Note) -> Unit,
    onRemoveNote: () -> Unit,
    showPremiumModal: () -> Unit,
) {

    var showRemoveNoteDialog by remember { mutableStateOf(false) }

    var isPaletteVisible by remember { mutableStateOf(false) }

    var selectedColor by remember(note.color) {
        mutableStateOf(note.color.toComposeColor())
    }
    val controller = rememberColorPickerController()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(8.dp),
                clip = true
            )
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)


    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            note.color.toComposeColor()
                        )
                        .clickable(onClick = {
                            if (appPreferences.isPremium) {
                                isPaletteVisible = !isPaletteVisible
                            } else {
                                showPremiumModal()
                            }
                        })
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = note.note,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showRemoveNoteDialog = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Note")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = note.selectedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )


            AnimatedVisibility(
                visible = isPaletteVisible,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        HsvColorPicker(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(350.dp)
                                .padding(10.dp),
                            controller = controller,
                            initialColor = selectedColor,
                            onColorChanged = { colorEnvelope ->
                                selectedColor = colorEnvelope.color
                            }
                        )

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(selectedColor)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val updatedNote =
                                    note.copy(color = selectedColor.toStringColor())
                                onUpdateNote(updatedNote)
                                isPaletteVisible = false
                            }
                        ) {
                            Text(stringResource(R.string.select))
                        }
                    }
                }
            }
        }
    }




    if (showRemoveNoteDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveNoteDialog = false },
            title = { Text(stringResource(R.string.remove_note)) },
            text = { Text(stringResource(R.string.dialog_content_remove_note)) },
            dismissButton = {
                Button(
                    onClick = { showRemoveNoteDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    onClick = {
                        onRemoveNote()
                        showRemoveNoteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.remove))
                }
            },
        )
    }
}

data class BookWithNotes(
    val book: Book,
    val notes: List<Note>
)
