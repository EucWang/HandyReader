package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.domain.model.ReadBgList

interface ReadBgsApi {

    suspend fun readBgList(page:Int, pageSize: Int) : Result<BaseResponse<ReadBgList>>
}