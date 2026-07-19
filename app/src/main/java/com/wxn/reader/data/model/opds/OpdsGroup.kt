package com.wxn.reader.data.model.opds

data class OpdsGroup(
    val title: String,
    val href: String,
    val entries: List<OpdsEntry> = emptyList()
)
