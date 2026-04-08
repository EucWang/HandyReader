package com.wxn.reader.domain.repository

import com.wxn.reader.domain.model.ReadBgData
import kotlinx.coroutines.flow.Flow

interface ReadBgRepository {


    /***
     * 获取背景图列表, 从网络获取
     * @param page 页码
     * @param pageSize 每页数量
     */
    suspend fun getReadBg(page: Int, pageSize: Int): Result<Pair<List<ReadBgData>?, Int?>> // Pair<items, totalPages>

    /**
     * 加载本地已经下载成功了的图片列表数据实体
     */
    fun getDownloadedReadBgs() : Flow<List<ReadBgData>>

    /****
     * 保持下载完成的数据到本地数据库中
     */
    suspend fun saveReadBg(readBg: ReadBgData)

    /****
     * 判断一个在线图片的id,是否在本地已经缓存好了
     */
    suspend fun isDownloaded(readBgItemId: String) : Boolean
}