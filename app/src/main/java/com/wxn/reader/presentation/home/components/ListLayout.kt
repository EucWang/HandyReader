package com.wxn.reader.presentation.home.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
//import com.google.android.gms.ads.AdError
//import com.google.android.gms.ads.AdRequest
//import com.google.android.gms.ads.FullScreenContentCallback
//import com.google.android.gms.ads.LoadAdError
//import com.google.android.gms.ads.interstitial.InterstitialAd
//import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.wxn.reader.BuildConfig
import com.wxn.reader.data.model.AppPreferences
import com.wxn.base.bean.Book
import com.wxn.reader.data.dto.FileType
import com.wxn.reader.data.dto.FileType.Companion.stringToFileType
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.presentation.home.HomeViewModel
import com.wxn.reader.navigation.Screens
import com.wxn.base.util.Logger
import kotlin.random.Random

@Composable
fun ListLayout(
    clearSearch: () -> Unit,
    books: LazyPagingItems<Book>,
    selectedBooks: List<Book>,
    selectionMode: Boolean,
    toggleSelection: (Book) -> Unit,
    viewModel: HomeViewModel,
    isLoading: Boolean,
    appPreferences: AppPreferences,
    openBook: (Book) -> Unit
) {
    val listAdUnit = BuildConfig.OPEN_BOOK_LIST_AD_UNIT
    val navController: NavHostController = LocalNavController.current

    val context = LocalContext.current
//    var mInterstitialAd by remember { mutableStateOf<InterstitialAd?>(null) }

    fun loadInterstitialAd() {
        if (!BuildConfig.ENABLE_AD) {
            return
        }
//        if (!appPreferences.isPremium) {//TODO
//            InterstitialAd.load(
//                context,
//                listAdUnit,
//                AdRequest.Builder().build(),
//                object : InterstitialAdLoadCallback() {
//                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
//                        mInterstitialAd = interstitialAd
//                    }
//
//                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
//                        mInterstitialAd = null
//                    }
//                }
//            )
//        }
    }

    fun showInterstitialAd(onAdDismissed: () -> Unit) {
        if (!BuildConfig.ENABLE_AD) {
            return
        }//TODO
//        if (!appPreferences.isPremium && mInterstitialAd != null) {
//            mInterstitialAd?.let { ad ->
//                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
//                    override fun onAdDismissedFullScreenContent() {
//                        mInterstitialAd = null
//                        loadInterstitialAd()
//                        onAdDismissed()
//                    }
//
//                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
//                        mInterstitialAd = null
//                        onAdDismissed()
//                    }
//                }
//                ad.show(context as Activity)
//            }
//        } else {
//            onAdDismissed()
//        }
    }

    // Load the ad when the composable is first created
    LaunchedEffect(Unit) {
        if (!appPreferences.isPremium) {
            loadInterstitialAd()
        }
    }


    val isAddingBook by viewModel.isAddingBooks.collectAsState()

    LazyColumn(
        userScrollEnabled = !isAddingBook,
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            count = books.itemCount,
            key = books.itemKey { book -> "${book.id}_${book.filePath}" }
        ) { index ->
            val book = books[index] ?: return@items
            val isSelected = selectedBooks.contains(book)

            Box(
                modifier = Modifier.animateItem()
            ) {
                BookListCard(
                    book = book,
                    openBook = openBook,
                    updateLastOpened = {
                        viewModel.updateBook(book.copy(lastOpened = System.currentTimeMillis()))
                    },
                    selected = isSelected,
                    selectionMode = selectionMode,
                    toggleSelection = {
                        toggleSelection(it)
                    },
                    isLoading = isLoading,
                    appPreferences = appPreferences,
                    viewModel = viewModel
                )
            }
        }
    }
}


