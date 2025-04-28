package com.wxn.bookparser.domain.category

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Parcelize
@Immutable
class Category(val name: String) : Parcelable {
    companion object {
        val DEFAULT = Category("default")
        val READING = Category("reading")
        val ALREADY_READ = Category("finished")
        val PLANNING = Category("planning")
        val DROPPED = Category("dropped")

        fun stringToCategory(cate: String) = when (cate) {
            "default" -> DEFAULT
            "reading" -> Category.READING
            "finished" -> Category.ALREADY_READ
            "planning" -> Category.PLANNING
            "dropped" -> Category.DROPPED
            else -> Category(cate)
        }
    }
}