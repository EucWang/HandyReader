package com.wxn.mobi.data.model

data class CountPair(
    val first: Int,
    val second: Int
) {
    fun toPair(): Pair<Int, Int> {
        return Pair<Int, Int>(first, second)
    }
}