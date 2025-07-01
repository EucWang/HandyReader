package com.wxn.reader.domain.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import com.wxn.reader.data.dto.AnnotationType
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
data class BookAnnotation(
    val id: Long = 0,           //id
    val bookId: Long,           //书id
    val locator: String,        //位置
    val color: String,          //颜色
    val note: String?,          //笔记
    val type: AnnotationType    //类型：高亮/下划线
) : Parcelable {
}
