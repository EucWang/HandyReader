package com.wxn.reader.domain.model


enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE;

    override fun toString(): String {
        return when(this) {
            HIGHLIGHT -> {
                TYPE_HIGHTLIGHT
            }
            UNDERLINE -> {
                TYPE_UNDERLINE
            }
        }
    }

    companion object {
        const val TYPE_HIGHTLIGHT = "highlight"
        const val TYPE_UNDERLINE = "underline"
    }
}