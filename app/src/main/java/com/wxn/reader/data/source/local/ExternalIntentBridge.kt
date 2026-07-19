package com.wxn.reader.data.source.local

import android.net.Uri
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

@ActivityRetainedScoped
class ExternalIntentBridge @Inject constructor() {
    private val _pendingUri = MutableSharedFlow<Uri>(replay = 1)
    val pendingUri: SharedFlow<Uri> = _pendingUri.asSharedFlow()

    private val _navigationRoute = Channel<String>(capacity = Channel.BUFFERED)
    val navigationRoute: Channel<String> = _navigationRoute

    private val _navigationError = Channel<String>(capacity = Channel.BUFFERED)
    val navigationError: Channel<String> = _navigationError

    fun submit(uri: Uri) {
        _pendingUri.tryEmit(uri)
    }

    fun submitNavigationRoute(route: String) {
        _navigationRoute.trySend(route)
    }

    fun submitNavigationError(message: String) {
        _navigationError.trySend(message)
    }

    fun clear() {
        _pendingUri.resetReplayCache()
    }
}
