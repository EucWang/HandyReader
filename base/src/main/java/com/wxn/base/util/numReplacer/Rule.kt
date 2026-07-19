package com.wxn.base.util.numReplacer


data class Rule(val pattern: Regex, val handler: (MatchResult) -> String, val priority: Int)
