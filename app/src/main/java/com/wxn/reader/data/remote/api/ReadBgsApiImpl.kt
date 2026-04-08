package com.wxn.reader.data.remote.api

import com.wxn.reader.data.remote.dto.BaseResponse
import com.wxn.reader.domain.model.ReadBgList
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadBgsApiImpl @Inject constructor(
    private val httpClient: HttpClient
) : ReadBgsApi{

    override suspend fun readBgList(page: Int, pageSize: Int): Result<BaseResponse<ReadBgList>> {
        return BaseApi.get<ReadBgList>(httpClient, ApiPath.API_READ_BGS,
           mapOf(
               "page" to page,
               "page_size" to pageSize
           )
        )
    }

}