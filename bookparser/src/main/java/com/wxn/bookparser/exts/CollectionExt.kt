package com.wxn.bookparser.exts

fun <T> MutableList<T>.addAll(calculation: () -> List<T>) {
    addAll(calculation())
}