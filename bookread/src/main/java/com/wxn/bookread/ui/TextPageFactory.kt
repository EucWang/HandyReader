package com.wxn.bookread.ui

//import com.wxn.bookread.ReadBook
import com.wxn.bookread.data.model.TextPage

class TextPageFactory(dataSource: IDataSource, val provider: PageViewDataProvider) :
    IPageFactory<TextPage>(dataSource) {

    /***
     * 是否有上页
     */
    override fun hasPrev(): Boolean = with(dataSource) {
        return hasPrevChapter() || pageIndex > 0
    }

    /***
     * 是否有下页
     */
    override fun hasNext(): Boolean = with(dataSource) {
        return hasNextChapter() || currentChapter?.isLastIndex(pageIndex) != true
    }

    /***
     * 是否有下下页
     */
    override fun hasNextPlus(): Boolean = with(dataSource) {
        return hasNextChapter() || pageIndex < (currentChapter?.pageSize ?: 1) - 2
    }


    override fun moveToFirst() {
        provider.setPageIndex(0)
    }

    override fun moveToLast() = with(dataSource) {
        currentChapter?.let {
            if (it.pageSize == 0) {
                provider.setPageIndex(0)
            } else {
                provider.setPageIndex(it.pageSize.minus(1))
            }
        } ?: provider.setPageIndex(0)
    }


    /***
     * 移动到下一页
     */
    override fun moveToNext(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasNext()) {
            if (currentChapter?.isLastIndex(pageIndex) == true) {
                provider.moveToNextChapter(upContent)
            } else {
                provider.setPageIndex(pageIndex.plus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
    }


    override fun moveToPrev(upContent: Boolean): Boolean = with(dataSource) {
        return if (hasPrev()) {
            if (pageIndex <= 0) {
                provider.moveToPrevChapter(upContent)
            } else {
                provider.setPageIndex(pageIndex.minus(1))
            }
            if (upContent) upContent(resetPageOffset = false)
            true
        } else
            false
    }


    override val currentPage: TextPage
        get() = with(dataSource) {
            provider.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                return@with it.page(pageIndex)
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPage: TextPage
        get() = with(dataSource) {
            provider.msg?.let {
                return@with TextPage(text = it).format()
            }
            currentChapter?.let {
                if (pageIndex < it.pageSize - 1) {
                    return@with it.page(pageIndex + 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
            }
            if (!hasNextChapter()) {
                return@with TextPage(text = "")
            }
            nextChapter?.let {
                return@with it.page(0)?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val prevPage: TextPage
        get() = with(dataSource) {
            provider.msg?.let {
                return@with TextPage(text = it).format()
            }
            if (pageIndex > 0) {
                currentChapter?.let {
                    return@with it.page(pageIndex - 1)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
            }
            prevChapter?.let {
                return@with it.lastPage?.removePageAloudSpan()
                    ?: TextPage(title = it.title).format()
            }
            return TextPage().format()
        }

    override val nextPagePlus: TextPage
        get() = with(dataSource) {
            currentChapter?.let {
                if (pageIndex < it.pageSize - 2) {
                    return@with it.page(pageIndex + 2)?.removePageAloudSpan()
                        ?: TextPage(title = it.title).format()
                }
                nextChapter?.let { nc ->
                    if (pageIndex < it.pageSize - 1) {
                        return@with nc.page(0)?.removePageAloudSpan()
                            ?: TextPage(title = nc.title).format()
                    }
                    return@with nc.page(1)?.removePageAloudSpan()
                        ?: TextPage(title = nc.title).format()
                }

            }
            return TextPage().format()
        }
}