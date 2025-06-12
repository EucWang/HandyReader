package com.wxn.base.bean

data class CssInfo(
    val identifier: String,
    val weight: Int,
    val isBaseSelector: Boolean,
    val datas: List<RuleData>
)

data class RuleData(
    val name: String,
    val value: String
)
