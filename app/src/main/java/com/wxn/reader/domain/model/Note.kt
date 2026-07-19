package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.base.bean.Locator
import kotlinx.parcelize.Parcelize


@Parcelize
@Immutable
data class Note (
    var id : Long = 0,
    var locator: String = "",
    var selectedText : String = "",
    var note : String = "",
    var color : String = "",
    var bookId: Long = 0,
    var createdAt: Long? = null,
    /** 跨设备稳定 UUID。新增时为 null(mapper 生成);编辑既有项时从 DB 透传,保证身份不变。 */
    var uuid: String? = null
) : Parcelable {

    val locatorInfo: Locator?
        get() {
            return Locator.fromJsonString(locator)
        }
}