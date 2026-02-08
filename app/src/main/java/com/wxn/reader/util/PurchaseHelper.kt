package com.wxn.reader.util

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PurchaseHelper(val activity: Activity) {

    private val _formattedPrice = MutableStateFlow("N/A")
    val formattedPrice = _formattedPrice.asStateFlow()
    private val _isPremium = MutableStateFlow(true)
    val isPremium = _isPremium.asStateFlow()

    fun makePurchase() {
    }
}
