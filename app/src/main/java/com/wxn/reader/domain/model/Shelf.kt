package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize


@Parcelize
@Immutable
data class Shelf (
    var id : Long = 0,
    var name : String = "",
    var order : Int = 0,
    /** 跨设备稳定 UUID。新增时为 null(mapper 生成);编辑既有项时从 DB 透传,保证身份不变。 */
    var uuid: String? = null
): Parcelable