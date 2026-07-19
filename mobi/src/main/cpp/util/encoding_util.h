//
// encoding_util.h — FB2 文件编码检测与转码工具
//
// 解决 tinyxml2 不支持非 UTF-8 编码的问题。
// 提供：XML 声明编码检测、单字节编码→UTF-8 全文件转码、
//       UTF-8 文件中孤立非法字节的字节级恢复。
//
// 参考：
//   Windows-1251 码点表：https://encoding.spec.whatwg.org/index-windows-1251.txt
//   KOI8-R 码点表：https://encoding.spec.whatwg.org/index-koi8-r.txt
//   ISO-8859-5 码点表：https://encoding.spec.whatwg.org/index-iso-8859-5.txt
//

#ifndef HANDYREADER_ENCODING_UTIL_H
#define HANDYREADER_ENCODING_UTIL_H

#include <string>
#include <fstream>
#include <cstdint>
#include <cstring>
#include <algorithm>
#include "utf8.h"
#include "log.h"

namespace encoding_util {

// ── 编码名称常量 ──────────────────────────────────────────────

static constexpr const char* ENC_UTF8      = "utf-8";
static constexpr const char* ENC_WIN1251   = "windows-1251";
static constexpr const char* ENC_KOI8R     = "koi8-r";
static constexpr const char* ENC_ISO8859_5 = "iso-8859-5";
static constexpr const char* ENC_UNKNOWN   = "unknown";

// ── 单字节编码 → Unicode 码点查表 ────────────────────────────
// 每张表 256 条目：index = 原始字节值(0x00-0xFF)，value = Unicode 码点。
// 0x00-0x7F 固定为 ASCII（码点 = 字节值），所有表相同。
// 0x80-0xFF 各编码不同。

// Windows-1251（最常见 FB2 非 UTF-8 编码）
// 来源：WHATWG Encoding Spec index-windows-1251
static constexpr uint32_t kWin1251[256] = {
    // 0x00-0x7F: ASCII
    0x0000,0x0001,0x0002,0x0003,0x0004,0x0005,0x0006,0x0007,
    0x0008,0x0009,0x000A,0x000B,0x000C,0x000D,0x000E,0x000F,
    0x0010,0x0011,0x0012,0x0013,0x0014,0x0015,0x0016,0x0017,
    0x0018,0x0019,0x001A,0x001B,0x001C,0x001D,0x001E,0x001F,
    0x0020,0x0021,0x0022,0x0023,0x0024,0x0025,0x0026,0x0027,
    0x0028,0x0029,0x002A,0x002B,0x002C,0x002D,0x002E,0x002F,
    0x0030,0x0031,0x0032,0x0033,0x0034,0x0035,0x0036,0x0037,
    0x0038,0x0039,0x003A,0x003B,0x003C,0x003D,0x003E,0x003F,
    0x0040,0x0041,0x0042,0x0043,0x0044,0x0045,0x0046,0x0047,
    0x0048,0x0049,0x004A,0x004B,0x004C,0x004D,0x004E,0x004F,
    0x0050,0x0051,0x0052,0x0053,0x0054,0x0055,0x0056,0x0057,
    0x0058,0x0059,0x005A,0x005B,0x005C,0x005D,0x005E,0x005F,
    0x0060,0x0061,0x0062,0x0063,0x0064,0x0065,0x0066,0x0067,
    0x0068,0x0069,0x006A,0x006B,0x006C,0x006D,0x006E,0x006F,
    0x0070,0x0071,0x0072,0x0073,0x0074,0x0075,0x0076,0x0077,
    0x0078,0x0079,0x007A,0x007B,0x007C,0x007D,0x007E,0x007F,
    // 0x80-0x8F
    0x0402,0x0403,0x201A,0x0453,0x201E,0x2026,0x2020,0x2021,
    0x20AC,0x0409,0x040A,0x040B,0x040C,0x040D,0x040E,0x040F,
    // 0x90-0x9F
    0x0452,0x2018,0x2019,0x201C,0x201D,0x2022,0x2013,0x2014,
    0x0459,0x045A,0x045B,0x045C,0x045D,0x045E,0x045F,0x0451,
    // 0xA0-0xAF
    0x00A0,0x040E,0x045E,0x0408,0x00A4,0x0490,0x00A6,0x00A7,
    0x0401,0x00A9,0x0404,0x00AB,0x00AC,0x00AD,0x00AE,0x0407,
    // 0xB0-0xBF
    0x00B0,0x00B1,0x0406,0x0456,0x0491,0x00B5,0x00B6,0x00B7,
    0x0451,0x2116,0x0454,0x00BB,0x0458,0x0405,0x0455,0x0457,
    // 0xC0-0xFF: Cyrillic А-Яа-я (codepoint = 0x0410 + (byte - 0xC0))
    0x0410,0x0411,0x0412,0x0413,0x0414,0x0415,0x0416,0x0417,
    0x0418,0x0419,0x041A,0x041B,0x041C,0x041D,0x041E,0x041F,
    0x0420,0x0421,0x0422,0x0423,0x0424,0x0425,0x0426,0x0427,
    0x0428,0x0429,0x042A,0x042B,0x042C,0x042D,0x042E,0x042F,
    0x0430,0x0431,0x0432,0x0433,0x0434,0x0435,0x0436,0x0437,
    0x0438,0x0439,0x043A,0x043B,0x043C,0x043D,0x043E,0x043F,
    0x0440,0x0441,0x0442,0x0443,0x0444,0x0445,0x0446,0x0447,
    0x0448,0x0449,0x044A,0x044B,0x044C,0x044D,0x044E,0x044F
};

// KOI8-R（Unix 传统俄文编码）
// 来源：WHATWG Encoding Spec index-koi8-r
static constexpr uint32_t kKoi8R[256] = {
    // 0x00-0x7F: ASCII
    0x0000,0x0001,0x0002,0x0003,0x0004,0x0005,0x0006,0x0007,
    0x0008,0x0009,0x000A,0x000B,0x000C,0x000D,0x000E,0x000F,
    0x0010,0x0011,0x0012,0x0013,0x0014,0x0015,0x0016,0x0017,
    0x0018,0x0019,0x001A,0x001B,0x001C,0x001D,0x001E,0x001F,
    0x0020,0x0021,0x0022,0x0023,0x0024,0x0025,0x0026,0x0027,
    0x0028,0x0029,0x002A,0x002B,0x002C,0x002D,0x002E,0x002F,
    0x0030,0x0031,0x0032,0x0033,0x0034,0x0035,0x0036,0x0037,
    0x0038,0x0039,0x003A,0x003B,0x003C,0x003D,0x003E,0x003F,
    0x0040,0x0041,0x0042,0x0043,0x0044,0x0045,0x0046,0x0047,
    0x0048,0x0049,0x004A,0x004B,0x004C,0x004D,0x004E,0x004F,
    0x0050,0x0051,0x0052,0x0053,0x0054,0x0055,0x0056,0x0057,
    0x0058,0x0059,0x005A,0x005B,0x005C,0x005D,0x005E,0x005F,
    0x0060,0x0061,0x0062,0x0063,0x0064,0x0065,0x0066,0x0067,
    0x0068,0x0069,0x006A,0x006B,0x006C,0x006D,0x006E,0x006F,
    0x0070,0x0071,0x0072,0x0073,0x0074,0x0075,0x0076,0x0077,
    0x0078,0x0079,0x007A,0x007B,0x007C,0x007D,0x007E,0x007F,
    // 0x80-0xFF: KOI8-R specific
    0x2500,0x2502,0x250C,0x2510,0x2514,0x2518,0x251C,0x2524,
    0x252C,0x2534,0x253C,0x2580,0x2584,0x2588,0x258C,0x2590,
    0x2591,0x2592,0x2593,0x2320,0x25A0,0x2219,0x221A,0x2248,
    0x2264,0x2265,0x00A0,0x2321,0x00B0,0x00B2,0x00B7,0x00F7,
    0x2550,0x2551,0x2552,0x0451,0x2553,0x2554,0x2555,0x2556,
    0x2557,0x2558,0x2559,0x255A,0x255B,0x255C,0x255D,0x255E,
    0x255F,0x2560,0x2561,0x0401,0x2562,0x2563,0x2564,0x2565,
    0x2566,0x2567,0x2568,0x2569,0x256A,0x256B,0x256C,0x00A9,
    0x044E,0x0430,0x0431,0x0446,0x0434,0x0435,0x0444,0x0433,
    0x0445,0x0438,0x0439,0x043A,0x043B,0x043C,0x043D,0x043E,
    0x043F,0x044F,0x0440,0x0441,0x0442,0x0443,0x0436,0x0432,
    0x044C,0x044B,0x0437,0x0448,0x044D,0x0449,0x0447,0x044A,
    0x042E,0x0410,0x0411,0x0426,0x0414,0x0415,0x0424,0x0413,
    0x0425,0x0418,0x0419,0x041A,0x041B,0x041C,0x041D,0x041E,
    0x041F,0x042F,0x0420,0x0421,0x0422,0x0423,0x0416,0x0412,
    0x042C,0x042B,0x0417,0x0428,0x042D,0x0429,0x0427,0x042A
};

// ISO-8859-5（国际标准西里尔编码）
// 来源：WHATWG Encoding Spec index-iso-8859-5
static constexpr uint32_t kIso88595[256] = {
    // 0x00-0x7F: ASCII
    0x0000,0x0001,0x0002,0x0003,0x0004,0x0005,0x0006,0x0007,
    0x0008,0x0009,0x000A,0x000B,0x000C,0x000D,0x000E,0x000F,
    0x0010,0x0011,0x0012,0x0013,0x0014,0x0015,0x0016,0x0017,
    0x0018,0x0019,0x001A,0x001B,0x001C,0x001D,0x001E,0x001F,
    0x0020,0x0021,0x0022,0x0023,0x0024,0x0025,0x0026,0x0027,
    0x0028,0x0029,0x002A,0x002B,0x002C,0x002D,0x002E,0x002F,
    0x0030,0x0031,0x0032,0x0033,0x0034,0x0035,0x0036,0x0037,
    0x0038,0x0039,0x003A,0x003B,0x003C,0x003D,0x003E,0x003F,
    0x0040,0x0041,0x0042,0x0043,0x0044,0x0045,0x0046,0x0047,
    0x0048,0x0049,0x004A,0x004B,0x004C,0x004D,0x004E,0x004F,
    0x0050,0x0051,0x0052,0x0053,0x0054,0x0055,0x0056,0x0057,
    0x0058,0x0059,0x005A,0x005B,0x005C,0x005D,0x005E,0x005F,
    0x0060,0x0061,0x0062,0x0063,0x0064,0x0065,0x0066,0x0067,
    0x0068,0x0069,0x006A,0x006B,0x006C,0x006D,0x006E,0x006F,
    0x0070,0x0071,0x0072,0x0073,0x0074,0x0075,0x0076,0x0077,
    0x0078,0x0079,0x007A,0x007B,0x007C,0x007D,0x007E,0x007F,
    // 0x80-0xAF: undefined/control in ISO-8859-5
    0x0080,0x0081,0x0082,0x0083,0x0084,0x0085,0x0086,0x0087,
    0x0088,0x0089,0x008A,0x008B,0x008C,0x008D,0x008E,0x008F,
    0x0090,0x0091,0x0092,0x0093,0x0094,0x0095,0x0096,0x0097,
    0x0098,0x0099,0x009A,0x009B,0x009C,0x009D,0x009E,0x009F,
    0x00A0,0x0401,0x0402,0x0403,0x0404,0x0405,0x0406,0x0407,
    0x0408,0x0409,0x040A,0x040B,0x040C,0x00AD,0x040E,0x040F,
    0x0410,0x0411,0x0412,0x0413,0x0414,0x0415,0x0416,0x0417,
    0x0418,0x0419,0x041A,0x041B,0x041C,0x041D,0x041E,0x041F,
    0x0420,0x0421,0x0422,0x0423,0x0424,0x0425,0x0426,0x0427,
    0x0428,0x0429,0x042A,0x042B,0x042C,0x042D,0x042E,0x042F,
    0x0430,0x0431,0x0432,0x0433,0x0434,0x0435,0x0436,0x0437,
    0x0438,0x0439,0x043A,0x043B,0x043C,0x043D,0x043E,0x043F,
    0x0440,0x0441,0x0442,0x0443,0x0444,0x0445,0x0446,0x0447,
    0x0448,0x0449,0x044A,0x044B,0x044C,0x044D,0x044E,0x044F,
    0x2116,0x0451,0x0452,0x0453,0x0454,0x0455,0x0456,0x0457,
    0x0458,0x0459,0x045A,0x045B,0x045C,0x00A7,0x045E,0x045F
};

// ── 内部工具函数 ──────────────────────────────────────────────

/** 将 Unicode 码点追加编码为 UTF-8 字节 */
inline void append_codepoint_to_utf8(std::string& out, uint32_t cp) {
    if (cp < 0x80) {
        out += static_cast<char>(cp);
    } else if (cp < 0x800) {
        out += static_cast<char>(0xC0 | (cp >> 6));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else if (cp < 0x10000) {
        out += static_cast<char>(0xE0 | (cp >> 12));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    } else if (cp < 0x110000) {
        out += static_cast<char>(0xF0 | (cp >> 18));
        out += static_cast<char>(0x80 | ((cp >> 12) & 0x3F));
        out += static_cast<char>(0x80 | ((cp >> 6) & 0x3F));
        out += static_cast<char>(0x80 | (cp & 0x3F));
    }
}

/**
 * 检查从 pos 开始的字节是否构成合法 UTF-8 多字节序列。
 * @return 合法序列长度(2-4)；不合法返回 0
 */
inline int utf8_sequence_length(const std::string& s, size_t pos) {
    if (pos >= s.size()) return 0;
    unsigned char c = static_cast<unsigned char>(s[pos]);

    if (c < 0x80) return 1;  // ASCII

    // 0x80-0xBF: 续字节，不能单独出现
    // 0xC0, 0xC1: RFC 3629 禁止（过长编码）
    // 0xF5-0xFF: 超出 Unicode 范围
    if (c < 0xC2) return 0;
    if (c > 0xF4) return 0;

    int seqLen;
    if (c <= 0xDF) seqLen = 2;
    else if (c <= 0xEF) seqLen = 3;
    else seqLen = 4;

    // 检查是否有足够字节
    if (pos + seqLen > s.size()) return 0;

    // 检查续字节
    for (int j = 1; j < seqLen; j++) {
        if ((static_cast<unsigned char>(s[pos + j]) & 0xC0) != 0x80)
            return 0;
    }

    // 过长编码检查
    if (c == 0xE0 && (static_cast<unsigned char>(s[pos + 1]) & 0x20) == 0)
        return 0;  // E0 后必须 A0-BF
    if (c == 0xED && (static_cast<unsigned char>(s[pos + 1]) & 0x20) != 0)
        return 0;  // ED 后必须 80-9F（禁止代理对）

    if (seqLen == 4) {
        if (c == 0xF0 && (static_cast<unsigned char>(s[pos + 1]) & 0x30) == 0)
            return 0;  // F0 后必须 90-BF
        if (c == 0xF4 && (static_cast<unsigned char>(s[pos + 1]) & 0x30) != 0)
            return 0;  // F4 后必须 80-8F
    }

    return seqLen;
}

// ── 公共 API ──────────────────────────────────────────────────

/**
 * 从文件原始字节中检测 XML encoding 属性。
 * 扫描前 2KB，查找 <?xml ... encoding="xxx" ... ?> 或 encoding='xxx'
 * @param raw 文件原始字节
 * @return 归一化编码名（小写）；找不到返回 "utf-8"（XML 默认）
 */
inline std::string detect_xml_encoding(const std::string& raw) {
    size_t scanLen = std::min(raw.size(), static_cast<size_t>(2048));

    // 大小写不敏感搜索 "encoding"
    for (size_t i = 0; i + 9 < scanLen; i++) {
        // 查找 "encoding" 关键词
        if (std::tolower(static_cast<unsigned char>(raw[i]))     == 'e' &&
            std::tolower(static_cast<unsigned char>(raw[i + 1])) == 'n' &&
            std::tolower(static_cast<unsigned char>(raw[i + 2])) == 'c' &&
            std::tolower(static_cast<unsigned char>(raw[i + 3])) == 'o' &&
            std::tolower(static_cast<unsigned char>(raw[i + 4])) == 'd' &&
            std::tolower(static_cast<unsigned char>(raw[i + 5])) == 'i' &&
            std::tolower(static_cast<unsigned char>(raw[i + 6])) == 'n' &&
            std::tolower(static_cast<unsigned char>(raw[i + 7])) == 'g')
        {
            size_t j = i + 8;
            // 跳过空白和 '='
            while (j < scanLen && (raw[j] == ' ' || raw[j] == '\t' || raw[j] == '=')) j++;
            if (j >= scanLen) break;

            // 读取引号内的值
            char quote = raw[j];
            if (quote == '"' || quote == '\'') {
                j++;
                size_t valStart = j;
                while (j < scanLen && raw[j] != quote && raw[j] != '\0') j++;
                if (j > valStart) {
                    std::string val(raw, valStart, j - valStart);
                    // 转小写
                    std::transform(val.begin(), val.end(), val.begin(),
                                   [](unsigned char ch) { return std::tolower(ch); });
                    // 别名归一化
                    if (val == "utf8" || val == "utf-8") return ENC_UTF8;
                    if (val == "windows-1251" || val == "cp1251" ||
                        val == "windows-cp1251" || val == "windows1251")
                        return ENC_WIN1251;
                    if (val == "koi8-r" || val == "koi8r" || val == "koi8")
                        return ENC_KOI8R;
                    if (val == "iso-8859-5" || val == "iso8859-5" || val == "iso_8859-5")
                        return ENC_ISO8859_5;
                    // 未知编码名，原样返回（用于日志）
                    return val;
                }
            }
        }
    }
    return ENC_UTF8;  // XML 规范：无 encoding 声明则默认 UTF-8
}

/**
 * 统计 UTF-8 无效字节占非 ASCII 字节的比例。
 * @return 0.0~1.0；纯 ASCII 返回 0.0
 */
inline double invalid_utf8_ratio(const std::string& s) {
    size_t nonAsciiTotal = 0;
    size_t invalidCount = 0;
    size_t i = 0;

    while (i < s.size()) {
        unsigned char c = static_cast<unsigned char>(s[i]);
        if (c < 0x80) {
            i++;
            continue;
        }

        nonAsciiTotal++;
        int seqLen = utf8_sequence_length(s, i);
        if (seqLen > 0) {
            // 合法序列，统计其中所有非 ASCII 字节
            nonAsciiTotal += (seqLen - 1);
            i += seqLen;
        } else {
            // 非法字节
            invalidCount++;
            i++;
        }
    }

    if (nonAsciiTotal == 0) return 0.0;
    return static_cast<double>(invalidCount) / static_cast<double>(nonAsciiTotal);
}

/**
 * 单字节编码 → UTF-8 全文件转码（查表法）。
 * @param input 原始字节
 * @param table 256 条码点表
 * @return UTF-8 字符串
 */
inline std::string single_byte_to_utf8(const std::string& input,
                                        const uint32_t table[256]) {
    std::string output;
    output.reserve(input.size() * 3 / 2);  // 预估：西里尔 1→2~3 字节
    for (size_t i = 0; i < input.size(); i++) {
        append_codepoint_to_utf8(output, table[static_cast<unsigned char>(input[i])]);
    }
    return output;
}

/**
 * 按编码名将单字节编码转码为 UTF-8。
 * @return 成功返回 UTF-8 字符串；不支持的编码返回空 string
 */
inline std::string to_utf8(const std::string& input, const std::string& encoding) {
    if (encoding == ENC_WIN1251)   return single_byte_to_utf8(input, kWin1251);
    if (encoding == ENC_KOI8R)     return single_byte_to_utf8(input, kKoi8R);
    if (encoding == ENC_ISO8859_5) return single_byte_to_utf8(input, kIso88595);
    return {};  // 不支持的编码
}

/**
 * 字节级恢复：扫描 UTF-8 内容，保留合法序列，
 * 对孤立非法字节（不在合法多字节序列中的 0x80-0xFF）按 Windows-1251 查表转为 UTF-8。
 *
 * 覆盖场景：UTF-8 文件中夹杂少量 Windows-1251 标点残留（转换工具遗漏）。
 * 对合法 UTF-8 内容是 no-op（不触发任何转换）。
 *
 * @param input 文件原始字节（声明为 UTF-8 或已通过 utf8::is_valid 检查）
 * @return 恢复后的 UTF-8 字符串
 */
inline std::string recover_invalid_bytes(const std::string& input) {
    std::string output;
    output.reserve(input.size());
    size_t i = 0;

    while (i < input.size()) {
        unsigned char c = static_cast<unsigned char>(input[i]);

        if (c < 0x80) {
            // ASCII
            output += static_cast<char>(c);
            i++;
            continue;
        }

        int seqLen = utf8_sequence_length(input, i);
        if (seqLen > 0) {
            // 合法 UTF-8 序列，直接拷贝
            output.append(input, i, seqLen);
            i += seqLen;
        } else {
            // 非法字节 → 按 Windows-1251 查表恢复
            append_codepoint_to_utf8(output, kWin1251[c]);
            i++;
        }
    }

    return output;
}

/**
 * 主入口：读取文件 + 检测编码 + 启发式验证 + 转码/恢复 → UTF-8。
 *
 * 决策流程：
 * 1. 读取文件原始字节
 * 2. 检测 XML encoding 声明
 * 3. 若声明为 windows-1251/koi8-r/iso-8859-5 → 全文件查表转码
 * 4. 若声明为 utf-8 或无声明 → 验证 UTF-8：
 *    - 合法 UTF-8 → 字节级恢复（no-op，直接返回）
 *    - 非法且密度 >10% → 判定 Windows-1251 未声明/错标 → 全文件转码
 *    - 非法且密度 ≤10% → 字节级恢复（修复孤立非法字节）
 * 5. 其他编码 → 字节级恢复（安全网）
 *
 * @param filepath    FB2 文件路径
 * @param outUtf8     [out] UTF-8 内容
 * @param outEncoding [out] 最终使用的编码名（用于日志）
 * @return true=成功；false=文件读取失败
 */
inline bool load_file_as_utf8(const std::string& filepath,
                               std::string& outUtf8,
                               std::string& outEncoding) {
    // 1. 读取文件原始字节
    std::ifstream ifs(filepath, std::ios::binary);
    if (!ifs.is_open()) {
        LOGE("encoding_util: failed to open file: %s", filepath.c_str());
        return false;
    }
    std::string raw((std::istreambuf_iterator<char>(ifs)),
                     std::istreambuf_iterator<char>());
    ifs.close();

    if (raw.empty()) {
        LOGE("encoding_util: file is empty: %s", filepath.c_str());
        return false;
    }

    // 2. 检测 XML encoding 声明
    std::string declared = detect_xml_encoding(raw);

    // 3. 声明的编码已知且非 UTF-8 → 全文件转码
    if (declared == ENC_WIN1251 || declared == ENC_KOI8R || declared == ENC_ISO8859_5) {
        outUtf8 = to_utf8(raw, declared);
        outEncoding = declared;
        if (outUtf8.empty()) {
            // 不支持的编码（不应到达此处，但防御性处理）
            outUtf8 = std::move(raw);
            outEncoding = ENC_UNKNOWN;
        } else {
            LOGI("encoding_util: declared [%s] → full-file conversion (%zu → %zu bytes)",
                 declared.c_str(), raw.size(), outUtf8.size());
        }
        return true;
    }

    // 4. 声明为 UTF-8 或无声明 → 启发式验证
    if (utf8::is_valid(raw)) {
        // 4a. 合法 UTF-8 → 字节级恢复（no-op）
        outUtf8 = recover_invalid_bytes(raw);
        outEncoding = ENC_UTF8;
        return true;
    }

    // 4b. 非法 UTF-8 → 检查无效密度
    double ratio = invalid_utf8_ratio(raw);
    if (ratio > 0.10) {
        // 高密度无效 → 判定 Windows-1251 未声明/错标
        outUtf8 = single_byte_to_utf8(raw, kWin1251);
        outEncoding = ENC_WIN1251;
        LOGW("encoding_util: declared [%s] but %.1f%% invalid UTF-8 → heuristic windows-1251",
             declared.c_str(), ratio * 100.0);
    } else {
        // 低密度无效 → 字节级恢复
        outUtf8 = recover_invalid_bytes(raw);
        outEncoding = ENC_UTF8;
        LOGW("encoding_util: %.1f%% invalid UTF-8 (local corruption) → byte-level recovery",
             ratio * 100.0);
    }
    return true;
}

} // namespace encoding_util

#endif //HANDYREADER_ENCODING_UTIL_H
