package com.wxn.reader.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import com.wxn.base.util.Logger
import com.wxn.reader.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun <T> rememberSaveableMutableStateOf(
    value: T,
    policy: SnapshotMutationPolicy<T> = structuralEqualityPolicy()
): MutableState<T> = rememberSaveable(init = { mutableStateOf(value, policy) })


class Ref(var value: Int)

@Suppress("NOTHING_TO_INLINE")
@Composable
inline fun LogCompositions(msg: String) {
    if (BuildConfig.DEBUG) {
        val ref = remember { Ref(0) }
        SideEffect { ref.value++ }
        Logger.d("Compositions: $msg ${ref.value}")
    }
}

@Composable
fun OnFirstLaunch(delayTime: Long = 300L, block: suspend CoroutineScope.() -> Unit) {
    var firstLaunch by rememberSaveableMutableStateOf(true)
    LaunchedEffect(null) {
        if (firstLaunch) {
            firstLaunch = false
            delay(delayTime)
            block.invoke(this)
        }
    }
}

@Composable
fun <T> OnLaunchFlow(key: Any? = null, emitter: () -> T, observer: suspend (value: T) -> Unit) {
    LaunchedEffect(key) {
        snapshotFlow(emitter).collect(observer)
    }
}
