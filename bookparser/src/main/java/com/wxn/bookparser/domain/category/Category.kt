package com.wxn.bookparser.domain.category

import androidx.compose.runtime.Immutable

@Immutable
enum class Category {
    READING,
    ALREADY_READ,
    PLANNING,
    DROPPED,
}

fun Category.toString() = when(this) {
    Category.READING -> "reading"
    Category.ALREADY_READ -> "finished"
    Category.PLANNING -> "planning"
    Category.DROPPED -> "dropped"
}

fun stringToCategory(cate:String) = when(cate) {
    "reading" -> Category.READING
    "finished" -> Category.ALREADY_READ
    "planning" -> Category.PLANNING
    "dropped" -> Category.DROPPED
    else -> Category.READING
}