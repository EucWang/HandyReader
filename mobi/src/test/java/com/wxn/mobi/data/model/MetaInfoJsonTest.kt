package com.wxn.mobi.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MetaInfoJsonTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testRoundTrip_allFields() {
        val original = MetaInfo(
            title = "Test Book",
            author = "Author \"Quote\" Name",
            contributor = "Contributor",
            subject = "Subject/Topic",
            publisher = "Publisher\nNew Line",
            date = "2024-01-01",
            description = "A \"description\" with \\backslash\\",
            review = "Great book!",
            imprint = "Imprint\tTab",
            copyright = "© 2024",
            isbn = "978-0-123456-78-9",
            asin = "B00EXAMPLE",
            language = "en",
            isEncrypted = true,
            coverPath = "/data/covers/cover.jpg",
            crc = 12345
        )
        val jsonString = json.encodeToString(MetaInfo.serializer(), original)
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals(original, decoded)
    }

    @Test
    fun testRoundTrip_defaultValues() {
        val original = MetaInfo()
        val jsonString = json.encodeToString(MetaInfo.serializer(), original)
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals(original, decoded)
        assertEquals(0, decoded.crc)
        assertEquals("", decoded.title)
        assertEquals(false, decoded.isEncrypted)
    }

    @Test
    fun testCrcField_nonZero() {
        val original = MetaInfo(crc = 0xDEADBEEF.toInt())
        val jsonString = json.encodeToString(MetaInfo.serializer(), original)
        assert(jsonString.contains("\"crc\":-559038737"))
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals(original.crc, decoded.crc)
    }

    @Test
    fun testContentHashField_roundTrip() {
        // ★ 2026-07-07 方案 A+:native 层算填 SHA-256,通过 JSON 回传给 Kotlin。
        //   见 docs/plans/2026-07-07-扫描导入同书去重.md §四-A+
        val hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val original = MetaInfo(crc = 0xDEADBEEF.toInt(), contentHash = hash)
        val jsonString = json.encodeToString(MetaInfo.serializer(), original)
        assert(jsonString.contains("\"contentHash\":\"$hash\""))
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals(hash, decoded.contentHash)
    }

    @Test
    fun testContentHashField_defaultEmpty() {
        // 老版本 native 不返回 contentHash 字段时,反序列化应为空字符串
        val jsonString = """{"title":"Old","crc":1}"""
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals("", decoded.contentHash)
    }

    @Test
    fun testSpecialCharacters_escapedCorrectly() {
        val original = MetaInfo(
            title = "Line1\nLine2\tTab\"Quote\"\\Backslash\bBell\fFormFeed",
            author = "Unicode: \u00e9\u00e8\u00ea\u00eb"
        )
        val jsonString = json.encodeToString(MetaInfo.serializer(), original)
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals(original, decoded)
    }

    @Test
    fun testUnknownKeysIgnored() {
        val jsonString = """{"title":"Test","extra_field":"ignored","crc":42}"""
        val decoded = json.decodeFromString(MetaInfo.serializer(), jsonString)
        assertEquals("Test", decoded.title)
        assertEquals(42, decoded.crc)
    }
}
