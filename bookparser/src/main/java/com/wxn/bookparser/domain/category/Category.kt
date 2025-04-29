package com.wxn.bookparser.domain.category

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

//@Parcelize
//@Immutable
//class Category(val name: String) : Parcelable {
//    companion object {
//        val DEFAULT = ("default")
//        val READING = ("reading")
//        val ALREADY_READ = ("finished")
//        val PLANNING = ("planning")
//        val DROPPED = ("dropped")
//
//        fun stringToCategory(cate: String) = when (cate) {
//            "default" -> DEFAULT
//            "reading" -> Category.READING
//            "finished" -> Category.ALREADY_READ
//            "planning" -> Category.PLANNING
//            "dropped" -> Category.DROPPED
//            else -> Category(cate)
//        }
//    }
//}