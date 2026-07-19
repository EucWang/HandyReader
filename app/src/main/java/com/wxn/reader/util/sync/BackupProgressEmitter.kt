package com.wxn.reader.util.sync

import androidx.annotation.StringRes
import com.wxn.reader.data.model.backup.BookSyncFailure
import com.wxn.reader.data.model.backup.BackupResult
import com.wxn.reader.data.model.backup.RestoreDiff
import com.wxn.reader.data.model.backup.UserDecision
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 备份/还原阶段(对应状态机各 Active 子态)。 */
enum class BackupPhase {
    HASH_CHECK, COMPUTING_HASH, EXPORTING, WRITING_SAF,
    READING_ZIP, DIFFING, RECEIVING_HLC, MERGING,
}

/**
 * ★ 严重-3:BackupProgressState 五态(交互态 HashPartial/ConfirmRestore 纳入同一对话框渲染)。
 *
 * ★ P1-6:Done 携带 [isRestore] 标记,用于区分备份/还原的 PartialFail 文案。
 */
sealed interface BackupProgressState {
    data object Idle : BackupProgressState
    data class Active(
        val phase: BackupPhase,
        @StringRes val detailResId: Int,
        val detailArgs: List<Any> = emptyList(),
        val progress: Float? = null,
    ) : BackupProgressState
    data class HashPartial(
        val inaccessibleBooks: List<String>,
        val hashFailedBooks: List<String>,
    ) : BackupProgressState
    data class ConfirmRestore(val diff: RestoreDiff) : BackupProgressState
    data class Done(val result: BackupResult, val isRestore: Boolean) : BackupProgressState
}

/**
 * 备份/还原进度发射器(Singleton,跨 Activity 重建存活)。
 *
 * - Exporter/Importer 调 [update] 推进进度,调 [awaitHashPartial]/[awaitRestoreConfirm] 挂起等待用户决策。
 * - UI 通过 [state] 收集当前状态渲染对话框。
 * - 用户决策通过 [resume] 发送,挂起的协程 resume。
 */
@Singleton
class BackupProgressEmitter @Inject constructor() {
    private val _state = MutableStateFlow<BackupProgressState>(BackupProgressState.Idle)
    val state: StateFlow<BackupProgressState> = _state.asStateFlow()

    /** 交互态用户决策 Channel(每次 await 创建新 Channel,避免跨次复用残留)。 */
    @Volatile
    private var decisionChannel: Channel<UserDecision>? = null

    fun update(
        phase: BackupPhase,
        @StringRes detailResId: Int,
        vararg detailArgs: Any,
        progress: Float? = null,
    ) {
        _state.value = BackupProgressState.Active(phase, detailResId, detailArgs.toList(), progress)
    }

    /** HashPartial 态:挂起等待用户选继续/取消。 */
    suspend fun awaitHashPartial(
        inaccessible: List<String>,
        hashFailed: List<String>,
    ): UserDecision {
        _state.value = BackupProgressState.HashPartial(inaccessible, hashFailed)
        return awaitDecision()
    }

    /** ConfirmRestore 态:挂起等待用户选确认/取消。 */
    suspend fun awaitRestoreConfirm(diff: RestoreDiff): UserDecision {
        _state.value = BackupProgressState.ConfirmRestore(diff)
        return awaitDecision()
    }

    private suspend fun awaitDecision(): UserDecision {
        val ch = Channel<UserDecision>(capacity = 1).also { decisionChannel = it }
        return ch.receive()
    }

    /** 用户决策回填(VM 调)。 */
    fun resume(decision: UserDecision) {
        decisionChannel?.trySend(decision)
        decisionChannel = null
    }

    /**
     * 标记操作完成。
     *
     * ★ P1-6:[isRestore] 区分备份(false)/还原(true),Done 态渲染 PartialFail 文案时按此选字符串。
     * - BackupExporter 调用方传 false
     * - BackupImporter 调用方传 true
     */
    fun finish(result: BackupResult, isRestore: Boolean) {
        _state.value = BackupProgressState.Done(result, isRestore)
    }

    fun reset() {
        _state.value = BackupProgressState.Idle
        decisionChannel = null
    }
}
