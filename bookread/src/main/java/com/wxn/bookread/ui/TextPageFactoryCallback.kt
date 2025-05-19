package com.wxn.bookread.ui

import com.wxn.base.bean.Book

interface TextPageFactoryCallback {

    var pageFactory : TextPageFactory?

    var book: Book?
}