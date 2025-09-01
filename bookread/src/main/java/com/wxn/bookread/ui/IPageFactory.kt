package com.wxn.bookread.ui

import com.wxn.base.bean.TextCssInfo
import com.wxn.base.bean.TextTag

abstract class IPageFactory<DATA>(protected val dataSource: IDataSource) {

    abstract fun moveToFirst()

    abstract fun moveToLast()

    /***
     * 移动到下一页
     */
    abstract fun moveToNext(upContent: Boolean): Boolean

    abstract fun moveToPrev(upContent: Boolean): Boolean

    abstract val nextPage: DATA

    abstract val prevPage: DATA

    abstract val currentPage: DATA

    abstract val nextPagePlus: DATA

    /***
     * 是否有下一章节
     */
    abstract fun hasNext(): Boolean

    /***
     * 是否有上一章节
     */
    abstract fun hasPrev(): Boolean

    abstract fun hasNextPlus(): Boolean

    abstract fun getPagesAnnotation(
        chapterIndex: Int,
        paragraphIndex: Int,
        lineStartOffset: Int,
        lineEndOffset: Int
    ): Pair<List<TextTag>, TextCssInfo?>
}