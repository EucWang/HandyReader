package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.domain.model.TTSModelsList

interface TTSModelsApi {

    suspend fun ttsModelsList(page: Int, pageSize: Int): Result<BaseResponse<TTSModelsList>>

}