package com.wxn.bookread.data.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * Direction of the reading progression across resources.
 */
@Serializable
public enum class ConfigReadingProgression(public val value: String) {
    @SerialName("ltr")
    LTR("ltr"),

    @SerialName("rtl")
    RTL("rtl");
}