package com.wxn.reader.presentation.ttsModels.viewmodels

import android.content.Context
import com.wxn.base.bean.DownloadFileType
import com.wxn.base.util.PathUtil
import com.wxn.reader.domain.model.DependencyIndex
import com.wxn.reader.domain.model.DependencyMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/****
 * Sherpa Model 的 依赖包 的元数据管理器
 */
@Singleton
class DependencyMetadataManager  @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dependenciesDir = File(context.filesDir, PathUtil.PATH_TTS_DEPENDENCIES)
    private val indexFile = File(dependenciesDir, ".index.json")

    init {
        dependenciesDir.mkdirs()  //创建对应目录
    }

    /****
     * 加载依赖索引配置文件, 从json中解析得到一个map集合
     */
    suspend fun loadIndex(): DependencyIndex = withContext(Dispatchers.IO) {
        if (!indexFile.exists()) {
            return@withContext DependencyIndex(emptyMap())
        }
        try {
            val json = indexFile.readText()
            Json.decodeFromString<DependencyIndex>(json)
        } catch (e: Exception) {
            DependencyIndex(emptyMap())
        }
    }

    suspend fun saveIndex(index: DependencyIndex) = withContext(Dispatchers.IO) {
        val json = Json.encodeToString(index)
        indexFile.writeText(json)
    }

    /****
     * 检测某个url的模型依赖 是否已经存在
     */
    suspend fun isDependencyDownloaded(url: String): Boolean = withContext(Dispatchers.IO) {
        val index = loadIndex()
        val metadata = index.dependencies[url]
        val targetPath  = metadata?.targetPath

        if (metadata?.extractedAt == null || targetPath.isNullOrEmpty()) return@withContext false

        // 检查解压目录是否存在
        val depDir = File(targetPath)
        depDir.exists()
    }

    /****
     * 更新依赖包的状态
     */
    suspend fun updateDependencyStatus(
        url: String,
        fileName: String,
        status: String //状态
    ) = withContext(Dispatchers.IO) {
        val index = loadIndex()
        val now = System.currentTimeMillis()

        var targetPath : String = ""
        val parentDir = PathUtil.getDownloadDir(context, DownloadFileType.TTS_DEPENDENCY)
        if (parentDir.exists()) {
            val files = parentDir.listFiles()
            if (files.isNotEmpty()) {
                for(file in files) {
                    if (file.isDirectory && file.name == fileName) {
                        targetPath = file.absolutePath
                        break
                    } else if (file.isFile && file.name.contains(fileName) && !file.name.endsWith(".zip")) {
                        targetPath =  file.absolutePath
                        break
                    }
                }
            }
        }

        val updatedMetadata = DependencyMetadata(
            url = url,
            fileName = fileName,
            targetPath = targetPath,
            downloadedAt = index.dependencies[url]?.downloadedAt ?: now,
            extractedAt = if (status == "completed") now else null,
            status = status
        )

        val updatedIndex = index.copy(
            dependencies = index.dependencies + (url to updatedMetadata)
        )

        saveIndex(updatedIndex)
    }
}