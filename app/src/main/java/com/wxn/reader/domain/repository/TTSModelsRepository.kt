package com.wxn.reader.domain.repository

import com.wxn.reader.domain.model.TTSModelData
import kotlinx.coroutines.flow.Flow

interface TTSModelsRepository {

    /***
     * 从网络加载可用的模型列表
     */
    suspend fun getModels(page: Int,
                          pageSize: Int):  Result<Pair<List<TTSModelData>?, Int?>>


    suspend fun getModelCard(url:String): Result<String>

    /***
     * 从本地数据库加载全部已经下载完成的模型数据
     */
    fun getDownloadedModels(): Flow<List<TTSModelData>>

    suspend fun saveModel(model : TTSModelData)

    suspend fun isDownloaded(modelId: String) : Boolean

    suspend fun getLocalModelByName(modelId: String): TTSModelData?

    suspend fun deleteModel(modelId: String)
}

