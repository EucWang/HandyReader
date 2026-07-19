package com.wxn.bookread.data.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


/**
 * Synthetic spread policy.
 */
@Serializable
public enum class ConfigSpread(public val value: String) {
    @SerialName("auto")
    AUTO("auto"),

    @SerialName("never")
    NEVER("never"),

    @SerialName("always")
    ALWAYS("always");
}