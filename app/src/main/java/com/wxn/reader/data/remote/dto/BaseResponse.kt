package com.wxn.reader.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
open class BaseResponse<T> {
    @SerialName("success")
    val success: Boolean? = false

    @SerialName("message")
    val message: String? = null

    @SerialName("detail")
    val detail: String? = null

    @SerialName("code")
    val code: String? = null

    @SerialName("data")
    val data: T? = null

    val pagination : Pagination? = null

}

@Serializable
data class Pagination(

    /***
     * 当前分页的索引
     */
    @SerialName("page")
    val page: Int = 0,  //": 1,

    /****
     * 当前页面中的条目数
     */
    @SerialName("page_size")
    val pageSize: Int = 0, //": 10,

    /***
     * 总条目数
     */
    @SerialName("total")
    val total: Int = 0,

    /***
     * 总页数
     */
    @SerialName("total_pages")
    val totalPages: Int = 0
)