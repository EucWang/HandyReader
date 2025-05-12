package com.wxn.bookread.data.model.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
public enum class ConfigImageFilter {
    @SerialName("darken")
    DARKEN,

    @SerialName("invert")
    INVERT;
}