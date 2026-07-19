package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.base.bean.Locator
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class BookAnnotation(
    val id: Long = 0,           //id
    val bookId: Long,           //书id
    val locator: String,        //位置
    val color: String,          //颜色
    val note: String?,          //笔记
    val type: AnnotationType,   //类型：高亮/下划线
    /** 跨设备稳定 UUID。新增时为 null(mapper 生成);编辑既有项时从 DB 透传,保证身份不变。 */
    val uuid: String? = null
) : Parcelable {

    val locatorInfo: Locator?
        get() {
            return Locator.fromJsonString(locator)
        }
}
