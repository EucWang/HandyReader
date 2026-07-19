package com.wxn.bookread.data.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
public enum class ConfigColumnCount {
    @SerialName("auto")
    AUTO,

    @SerialName("1")
    ONE,

    @SerialName("2")
    TWO;
}