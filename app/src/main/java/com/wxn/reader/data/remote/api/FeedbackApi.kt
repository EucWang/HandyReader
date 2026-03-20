package com.wxn.reader.data.remote.api
import com.wxn.reader.data.remote.dto.FeedbackRequest
import com.wxn.reader.data.remote.dto.FeedbackResponse
import kotlinx.coroutines.flow.Flow
/**
 * 反馈 API 接口
 */
interface FeedbackApi {
    /**
     * 提交用户反馈
     * @param request 反馈请求数据
     * @return Result<FeedbackResponse> 成功或错误信息
     */
    suspend fun submitFeedback(request: FeedbackRequest): Result<FeedbackResponse>
}