package com.wxn.reader.data.model.opds

data class OpdsFacet(
    val title: String,
    val href: String,
    val group: String? = null,
    val isActive: Boolean = false
)
