package com.wxn.reader.presentation.bookReader.components.modals.readbglist


import android.content.Context
import com.wxn.base.util.Coroutines
import com.wxn.base.util.PathUtil
import com.wxn.reader.R
import com.wxn.reader.domain.model.ReadBgData
import com.wxn.reader.domain.repository.ReadBgRepository
import com.wxn.reader.ui.theme.stringResource
import com.wxn.reader.util.Downloader
import io.ktor.client.HttpClient
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class BgImageDownloadManager @Inject constructor(
    private val context: Context,
    private val repository: ReadBgRepository,
    private val httpClient: HttpClient
) {
    companion object {
        private const val MAX_CONCURRENT_DOWNLOADS = 10
    }

    private val scope = Coroutines.scope()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates


    private val activeDownloads = mutableSetOf<String>()

    /****
     * 下载背景图数据
     * @param bgDataItem 背景图数据
     */
    fun downloadBgImageData(
        bgDataItem: ReadBgData,
        onCompleted: (ReadBgData) -> Unit,
        onError: (String) -> Unit
    ) {
        if (activeDownloads.size >= MAX_CONCURRENT_DOWNLOADS) {
           //"已达到最大下载数量"
            onError(stringResource(R.string.error_reach_max_download_limit))
            return
        }

        if (activeDownloads.contains(bgDataItem.id)) {
            return
        }

        activeDownloads.add(bgDataItem.id)

        updateState(bgDataItem.id,
            DownloadState(
                id = bgDataItem.id,
                isDownloading = true
            ))

        val job = scope.launch {
            try {
                val targetFile = File(PathUtil.getBgImageDownloadDir(context), bgDataItem.id + ".webp")
                val localPath = Downloader(httpClient).downloadToFile(bgDataItem.url, targetFile, onProgress = {})
                val retItem = bgDataItem.copy(
                    path = localPath,
                    isDownloaded = true
                )
                repository.saveReadBg(retItem)
                updateState(retItem.id, DownloadState(
                    id = retItem.id,
                    isDownloading = false,
                    isCompleted = true
                ))
                onCompleted(retItem)
            } catch (e: Exception) {
                updateState(bgDataItem.id,
                    DownloadState(
                        id = bgDataItem.id,
                        isDownloading = false,
                        error = e.message
                    ))
                onError(e.message ?: stringResource(R.string.download_failed) /*"下载失败"*/)
            } finally {
                activeDownloads.remove(bgDataItem.id)
            }
        }
    }

    private fun updateState(textureId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(textureId, state)
        }
    }

    fun getDownloadState(textureId: String): DownloadState? {
        return _downloadStates.value[textureId]
    }

    fun cancelDownload(itemId: String) {
        activeDownloads.remove(itemId)
        updateState(itemId, DownloadState(id = itemId))
    }

    fun release() {
        try {
            scope.cancel()
        } catch (ex : Exception) {

        }
    }
}