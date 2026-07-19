package com.wxn.reader.data.repository

import com.wxn.reader.data.mapper.readbg.ReadBgMapper
import com.wxn.reader.data.remote.api.ReadBgsApi
import com.wxn.reader.data.source.local.dao.ReadBgDao
import com.wxn.reader.domain.model.ReadBgData
import com.wxn.reader.domain.repository.ReadBgRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadBgRepositoryImpl @Inject constructor(
    private val api: ReadBgsApi,
    private val dao: ReadBgDao,
    private val readBgMapper: ReadBgMapper
) : ReadBgRepository {

    /***
     * 获取背景图列表, 从网络获取
     * @param page 页码
     * @param pageSize 每页数量
     */
    override suspend fun getReadBg(
        page: Int,
        pageSize: Int
    ): Result<Pair<List<ReadBgData>?, Int?>> /* Pair<items, totalPages> */ {
        return api.readBgList(page, pageSize).map { response ->
            Pair(response.data?.list, response.pagination?.totalPages)
        }
    }

    /**
     * 加载本地已经下载成功了的图片列表数据实体
     */
    override fun getDownloadedReadBgs(): Flow<List<ReadBgData>> {
        return dao.getAllDownloaded().map { entities ->
            entities.mapNotNull { entity ->
                readBgMapper.toReadBg(entity)
            }
        }
    }

    /****
     * 保持下载完成的数据到本地数据库中
     */
    override suspend fun saveReadBg(readBg: ReadBgData) {
        dao.insert(readBgMapper.toReadBgEntity(readBg))
    }

    /****
     * 判断一个在线图片的id,是否在本地已经缓存好了
     */
    override suspend fun isDownloaded(readBgItemId: String): Boolean {
        return dao.getDownloadById(readBgItemId) != null
    }
}