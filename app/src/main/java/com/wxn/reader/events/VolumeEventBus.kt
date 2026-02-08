package com.wxn.reader.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object VolumeEventBus {
    private val _volumeUpEvents = MutableSharedFlow<Unit>()
    val volumeUpEvents = _volumeUpEvents.asSharedFlow()

    private val _volumeDownEvents = MutableSharedFlow<Unit>()
    val volumeDownEvents = _volumeDownEvents.asSharedFlow()

    suspend fun emitVolumeUp() {
        _volumeUpEvents.emit(Unit)
    }

    suspend fun emitVolumeDown() {
        _volumeDownEvents.emit(Unit)
    }
}