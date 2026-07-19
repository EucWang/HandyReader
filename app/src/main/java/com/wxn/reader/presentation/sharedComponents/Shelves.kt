package com.wxn.reader.presentation.sharedComponents


import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wxn.reader.R
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.domain.model.Shelf
import com.wxn.reader.navigation.LocalNavController
import com.wxn.reader.navigation.Screens
import com.wxn.reader.presentation.sharedComponents.dialogs.AddShelfDialog
import com.wxn.reader.util.SafeScrollableTabRow

@Composable
fun Shelves(
    appPreferences: AppPreferences,
    shelves: List<Shelf>,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onShowAddShelfDialog: ()->Unit
) {
    val navController = LocalNavController.current


    SafeScrollableTabRow(
        selectedTabIndex = selectedTab,
        totalTabCount = shelves.size + 2,
        edgePadding = 16.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Tab(
            text = { Text(stringResource(R.string.all_books),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium) },
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) }
        )
        shelves.forEachIndexed { index, shelf ->
            Tab(
                text = { Text(shelf.name,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium) },
                selected = selectedTab == index + 1,
                onClick = { onTabSelected(index + 1) }
            )
        }
        Tab(
            icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = "New Shelf") },
            selected = false,
            onClick = onShowAddShelfDialog
        )
    }

//    if (showPremiumModal) {
//        PremiumModal(
//            purchaseHelper = purchaseHelper,
//            hidePremiumModal = { showPremiumModal = false }
//        )
//    }
}



