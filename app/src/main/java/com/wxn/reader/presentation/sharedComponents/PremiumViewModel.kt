package com.wxn.reader.presentation.sharedComponents

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wxn.reader.data.model.AppPreferences
import com.wxn.reader.data.source.local.AppPreferencesUtil
import com.wxn.reader.util.PurchaseHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject




@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val appPreferencesUtil: AppPreferencesUtil,
    application: Application,
) : AndroidViewModel(application) {

    private val _appPreferences = MutableStateFlow<AppPreferences?>(null)
    val appPreferences: StateFlow<AppPreferences?> = _appPreferences.asStateFlow()

    init {
        viewModelScope.launch {
            appPreferencesUtil.appPrefsFlow.stateIn(viewModelScope).collect { preferences ->
                _appPreferences.value = preferences
            }
        }
    }

    fun updatePremiumStatus(isPremium: Boolean) {
        viewModelScope.launch {
            val prefs = _appPreferences.value ?: return@launch
//            val currentPreferences = appPreferencesUtil.appPrefsFlow.firstOrNull()
            if (prefs.isPremium != isPremium) {
                val updatedPreferences = prefs.copy(isPremium = isPremium)
                appPreferencesUtil.updateAppPreferences(updatedPreferences)
                _appPreferences.value = updatedPreferences
            }
        }
    }

    fun purchasePremium(purchaseHelper: PurchaseHelper, onPurchaseComplete: () -> Unit) {
        purchaseHelper.makePurchase()
        viewModelScope.launch {
            purchaseHelper.isPremium.collect { isPremium ->
                updatePremiumStatus(isPremium)
                if (isPremium) {
                    onPurchaseComplete()
                }
            }
        }
    }

//    fun purchasePremium(purchaseHelper: PurchaseHelper) {
//        purchaseHelper.makePurchase()
//        viewModelScope.launch {
//            purchaseHelper.isPremium.collect { isPremium ->
//                updatePremiumStatus(isPremium)
//            }
//        }
//    }

}