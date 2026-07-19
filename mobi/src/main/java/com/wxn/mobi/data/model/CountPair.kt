package com.wxn.mobi.data.model

data class CountPair(
    val first: Int,
    val second: Int,
    val third: Int
) {
//    fun toPair(): Pair<Int, Int> {
//        return Pair<Int, Int>(first, second + third)
//    }

    fun toTriple(): Triple<Int, Int, Int> {
        return Triple<Int, Int, Int>(first, second, third)
    }
}