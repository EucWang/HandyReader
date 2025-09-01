package com.wxn.bookread.data.model.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.Locale

/**
 * Represents a language with its region.
 *
 * @param code BCP-47 language code
 */
@Serializable(with = ConfigLanguage.Serializer::class)
public class ConfigLanguage(code: String) {

    /**
     * Creates a [Language] from a Java [Locale].
     */
    public constructor(locale: Locale) : this(code = locale.toLanguageTag())

    /**
     * BCP-47 language code.
     */
    public val code: String = code.replace("_", "-")

    public val locale: Locale by lazy { Locale.forLanguageTag(code) }

    /** Indicates whether this language is a regional variant. */
    public val isRegional: Boolean by lazy {
        locale.country.isNotEmpty()
    }

    /** Returns this [Language] after stripping the region. */
    public fun removeRegion(): ConfigLanguage =
        ConfigLanguage(code.split("-", limit = 2).first())

    override fun toString(): String =
        "Language($code)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (code != (other as ConfigLanguage).code) return false
        return true
    }

    override fun hashCode(): Int =
        code.hashCode()

    internal object Serializer : KSerializer<ConfigLanguage> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
            "Language",
            PrimitiveKind.STRING
        )

        override fun serialize(encoder: Encoder, value: ConfigLanguage) {
            encoder.encodeString(value.code)
        }

        override fun deserialize(decoder: Decoder): ConfigLanguage =
            ConfigLanguage(decoder.decodeString())
    }
}
