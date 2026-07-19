package com.wxn.base.bean

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class Bookmark(
    var id: Long = 0,
    var bookId: Long = 0,
    var chapterIndex: Int = 0,
    var locator: String = "",
    var dateAndTime: Long = 0,
    var color: String? = null,
    /** 跨设备稳定 UUID。新增时为 null(mapper 生成);编辑既有项时从 DB 透传,保证身份不变。 */
    var uuid: String? = null
) : Parcelable{

    val locatorInfo: Locator?
        get() {
            return Locator.fromJsonString(locator)
        }
}