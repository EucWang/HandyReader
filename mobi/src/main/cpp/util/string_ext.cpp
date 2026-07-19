//
// Created by wxn on 2025/4/14.
//

#include "string_ext.h"
#include <string>
#include <vector>
#include <cctype>
#include <algorithm>
#include <utility>
#include <unordered_map>

static size_t ci_find(const std::string &haystack, const std::string &needle, size_t pos = 0) {
    if (needle.empty() || haystack.size() < pos + needle.size()) return std::string::npos;
    for (size_t i = pos; i <= haystack.size() - needle.size(); i++) {
        bool match = true;
        for (size_t j = 0; j < needle.size(); j++) {
            if (std::tolower(static_cast<unsigned char>(haystack[i + j])) !=
                std::tolower(static_cast<unsigned char>(needle[j]))) {
                match = false;
                break;
            }
        }
        if (match) return i;
    }
    return std::string::npos;
}

static size_t ci_rfind(const std::string &haystack, const std::string &needle) {
    if (needle.empty() || haystack.size() < needle.size()) return std::string::npos;
    for (size_t i = haystack.size() - needle.size(); i != std::string::npos; i--) {
        bool match = true;
        for (size_t j = 0; j < needle.size(); j++) {
            if (std::tolower(static_cast<unsigned char>(haystack[i + j])) !=
                std::tolower(static_cast<unsigned char>(needle[j]))) {
                match = false;
                break;
            }
        }
        if (match) return i;
    }
    return std::string::npos;
}

static std::regex regex(R"([\n\r\t\f\v]+)");
static std::string emptyStr1 = "&nbsp;";    // //不换行空格
static std::string emptyStr2 = "&ensp;";    //  //半角空格
static std::string emptyStr3 = "&emsp;"; //全角空格

static std::string emptyStr4 = "&thinsp;"; //窄空格
static std::string emptyStr5 = "&zwnj;";   //零宽不连字，不打印字符，放在电子文本的两个字符之间，抑制本来会发生的连字
//零宽连字，zero width joiner, 不打印字符，放在某些需要复杂排版语言（阿拉伯语，印地语）的两个字符之间，使得两个本不会发生连字的字符产生连字效果，
// 零宽字符的Unicode码位是U+200D(HTML: &#8205; &zwj;)
static std::string emptyStr6 = "&zwj";

static std::string emptyStr7 = "&#x0020;"; //空格
static std::string emptyStr8 = "&#x0009;"; //制表位
static std::string emptyStr9 = "&#x000A;"; //换行

static std::string emptyStr10 = "&#x000D;";  //回车
static std::string emptyStr11 = "&#12288;";

static std::string my_blank_str = " ";
static std::string my_empty_str = "";
static std::string my_joiner = "-";

namespace fs = std::filesystem;

bool string_ext::startWith(const std::string &str, const std::string &prefix) {
    if (prefix.empty()) return true;
    if (str.size() < prefix.size()) return false;
    return str.substr(0, prefix.size()) == prefix;
}

void string_ext::removeHtmlTagWrap(std::string &page_css_style, const std::string &tag_name) {
    if (page_css_style.empty()) {
        return;
    }
    string_ext::trim(page_css_style);
    if (page_css_style.empty()) {
        return;
    }

    std::string start_tag_pre = "<" + tag_name;
    std::string end_tag = "</" + tag_name + ">";

    if(!string_ext::startWith(page_css_style, start_tag_pre) || !endsWith(page_css_style, end_tag)) {
        return;
    }

    size_t pos = page_css_style.find('>');
    if(pos == std::string::npos) {
        return;
    }

    page_css_style.erase(0, pos + 1); //移除前面n个字符
    page_css_style.erase(page_css_style.size() - end_tag.size(), page_css_style.size());  //移除后面的字符
    trim(page_css_style);

    remove_c_style_comments(page_css_style); //移除注释
    string_ext::unescape_html(page_css_style);  //替换字符串中的HTML特使标识
    trim(page_css_style);
}

#include <string>

/**
 * 移除字符串中所有 C 风格注释
 */
void string_ext::remove_c_style_comments(std::string& input) {
    size_t write_pos = 0;          // 当前可写入的位置
    size_t i = 0;
    const size_t len = input.length();

    while (i < len) {
        // 检测注释开始 "/*"
        if (i + 1 < len && input[i] == '/' && input[i + 1] == '*') {
            // 跳过 "/*"
            i += 2;
            // 查找对应的 "*/"
            size_t end = input.find("*/", i);
            if (end == std::string::npos) {
                // 没有找到结束符，剩余部分全部丢弃
                break;
            } else {
                // 跳过整个注释内容及 "*/"
                i = end + 2;
                // 继续循环，不复制任何字符
            }
        } else {
            // 普通字符，移动到前面
            input[write_pos++] = input[i++];
        }
    }
    // 调整字符串长度为实际有效字符数
    input.resize(write_pos);
}

/***
 * 统计utf8的字符数， 常规的std::string的size，lenght字符有问题
 * @param utf8_str
 * @return
 */
size_t string_ext::utf8Count(const std::string &utf8_str) {
    try {
        std::list<std::string> strlist;
        strlist.push_front(utf8_str);
        size_t count = 0;
        do {
            std::string item = strlist.back();
            strlist.pop_back();
            if (utf8::is_valid(item)) {
                count += utf8::distance(item.begin(), item.end());
            } else {
                count += 1;
                size_t index = utf8::find_invalid(item);
                if (index != std::string::npos && index < item.length()) {
                    std::string part1 = item.substr(0, index);
                    if (!part1.empty()) {
                        strlist.push_front(part1);
                    }
                    if (index + 1 < item.length()) {
                        std::string part2 = item.substr(index + 1);
                        if (!part2.empty()) {
                            strlist.push_front(part2);
                        }
                    }
                }
            }
        } while (!strlist.empty());
        return count;
    } catch (utf8::invalid_utf8 &e) {
        LOGE("%s:failed invalid utf8[%s], %s", __func__, utf8_str.c_str(), e.what());
        return 0;
    } catch (utf8::not_enough_room &e) {
        LOGE("%s:failed not enought room utf8[%s], %s", __func__, utf8_str.c_str(), e.what());
        return 0;
    }
}

/**** * 创建随机的UUID
 * @return
 */
std::string string_ext::generate_uuid() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 15);
    std::uniform_int_distribution<> dis2(8, 11);

    std::stringstream ss;
    int i;
    ss << std::hex;
    for (i = 0; i < 8; i++) {
        ss << dis(gen);
    }
    ss << "-";
    for (i = 0; i < 4; i++) {
        ss << dis(gen);
    }
    ss << "-";
    ss << dis2(gen);
    for (i = 0; i < 3; i++) {
        ss << dis(gen);
    }
    ss << "-";
    ss << dis(gen) % 4 + 8;
    for (i = 0; i < 3; i++) {
        ss << dis(gen);
    }
    ss << "-";
    for (i = 0; i < 12; i++) {
        ss << dis(gen);
    };
    return ss.str();
}

int string_ext::toInt(std::string value) {
    try {
        return std::stoi(value);
    } catch (const std::invalid_argument &e) {
        LOGE("%s failed, %s, invalide argument: %s", __func__, e.what(), value.c_str());
    } catch (const std::out_of_range &e) {
        LOGE("%s failed, %s, Out of range: %s", __func__, e.what(), value.c_str());
    }
    return 0;
}

/****
 * 替换文件的后缀名
 * @param filePath
 * @param newExt
 * @return
 */
std::string string_ext::replaceExtension(const std::string &filePath, const std::string &newExt) {
    fs::path path(filePath);

    // 替换后缀名
    if (path.has_extension()) {
        path.replace_extension(newExt);
    } else {
        // 如果没有后缀名，直接追加新后缀
        path += newExt;
    }

    return path.string();
}

std::vector<std::string> string_ext::split(const std::string &s, char delimiter) {
    std::vector<std::string> tokens;
    std::string token;

    for (auto &c: s) {
        if (c == delimiter) {
            if (!token.empty()) {
                tokens.push_back(token);
                token.clear();
            }
        } else token += c;
    }
    if (!token.empty()) {
        tokens.push_back(token);
    }
    return tokens;
}


//std://trim from start(in-place)
void string_ext::ltrim(std::string &s) {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
}

//trim from end(in-place)
void string_ext::rtrim(std::string &s) {
    s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), s.end());
}

//trim from both ends(in-place)
void string_ext::trim(std::string &s) {
    ltrim(s);
    rtrim(s);
}

//返回新字符串的版本(非原地修改)
std::string string_ext::trim_copy(std::string s) {
    trim(s);
    return s;
}

bool string_ext::endsWith(const std::string &str, const std::string &suffix) {
    return str.size() >= suffix.size() &&
           std::equal(suffix.rbegin(), suffix.rend(), str.rbegin());
}

bool string_ext::endsWithIgnoreCase(std::string str, std::string suffix) {
    if (suffix.size() > str.size())return false;
    std::transform(str.begin(), str.end(), str.begin(), tolower);
    std::transform(suffix.begin(), suffix.end(), suffix.begin(), tolower);
    return str.compare(str.size() - suffix.size(), suffix.size(), suffix) == 0;
}

void string_ext::replace_all(std::string &input, std::string &old_str, std::string &new_str) {
    size_t pos = 0;
    while ((pos = input.find(old_str, pos)) != std::string::npos) {
        input.replace(pos, old_str.length(), new_str);
        pos += new_str.length();
    }
}


std::string string_ext::cleanStr(const std::string &str) {
    std::string ret = str;
//    trim(ret);
    ret = std::regex_replace(ret, regex, my_empty_str);
    replace_all(ret, emptyStr1, my_blank_str); //不换行空格
    replace_all(ret, emptyStr2, my_blank_str);         //半角空格
    replace_all(ret, emptyStr3, my_blank_str);         //全角空格

    replace_all(ret, emptyStr4, my_blank_str);       //窄空格
    replace_all(ret, emptyStr5, my_empty_str);        //零宽不连字，不打印字符，放在电子文本的两个字符之间，抑制本来会发生的连字
    //零宽连字，zero width joiner, 不打印字符，放在某些需要复杂排版语言（阿拉伯语，印地语）的两个字符之间，使得两个本不会发生连字的字符产生连字效果，
    // 零宽字符的Unicode码位是U+200D(HTML: &#8205; &zwj;)
    replace_all(ret, emptyStr6, my_joiner);
    replace_all(ret, emptyStr7, my_blank_str);       //空格
    replace_all(ret, emptyStr8, my_blank_str);       //制表位
    replace_all(ret, emptyStr9, my_empty_str);       //换行
    replace_all(ret, emptyStr10, my_empty_str);        //回车
    replace_all(ret, emptyStr11, my_empty_str);        //

    return ret;
}

/***
 * 判断是不是纯数字
 * @param value
 * @return
 */
bool string_ext::is_number(std::string &str) {
    bool hasDot = false;
    bool hastive = false;   //是否有正负符号
    bool ret = true;
    for (int i = 0; i < str.length(); ++i) {
        char ch = str[i];
        if (i == 0) {
            if (!std::isdigit(ch) && ch != '+' && ch != '-') {
                ret = false;
                break;
            }
        } else {
            if (ch == '.' && !hasDot) {
                hasDot = true;
                continue;
            }
            if (!std::isdigit(ch)) {
                ret = false;
                break;
            }
        }
    }
    return ret;
}

/****
 * 移除一段html中的所有标签，只保留纯文字显示内容
 * @param html
 * @return
 */
std::string string_ext::stripTags(const std::string &html) {
    std::string text;
    text.reserve(html.size());
    bool inTag = false;
    for (size_t i = 0; i < html.size(); i++) {
        if (html[i] == '<') { inTag = true; continue; }
        if (html[i] == '>') { inTag = false; continue; }
        if (!inTag) text += html[i];
    }
    return text;
}

std::string string_ext::to_lower(const std::string &str) {
    std::string result = str;
    std::transform(result.begin(), result.end(), result.begin(),
                   [](unsigned char c) { return std::tolower(c); });
    return result;
}

std::string string_ext::base_url_decode(const std::string &encoded) {
    if (encoded.empty()) {
        return encoded;
    }
    std::ostringstream decoded;
    for (size_t i = 0; i < encoded.length(); ++i) {
        if (encoded[i] == '%' && i + 2 < encoded.length()) {
            // 十六进制转字节：%20 → 0x20
            const char high = std::isxdigit(encoded[i+1]) ?
                              (encoded[i+1] >= 'A' ? (encoded[i+1] & 0xDF) - 'A' + 10 : encoded[i+1] - '0') : -1;
            const char low = std::isxdigit(encoded[i+2]) ?
                             (encoded[i+2] >= 'A' ? (encoded[i+2] & 0xDF) - 'A' + 10 : encoded[i+2] - '0') : -1;

            if (high != -1 && low != -1) {
                decoded << static_cast<char>((high << 4) | low);
                i += 2;  // 跳过已处理的 %XX
            } else {
                decoded << encoded[i];  // 非法编码保留 %
            }
        } else if (encoded[i] == '+') {
            decoded << ' ';  // + 替换为空格 [[3]][[8]]
        } else {
            decoded << encoded[i];  // 普通字符直接保留
        }
    }
    return decoded.str();
}

void string_ext::unescape_html(std::string &content) {
    if (content.empty()) {
        return;
    }
    // 使用数组存储实体（线性查找更快）
    const std::pair<std::string_view, char> entities[] = {
            {"&lt;", '<'},
            {"&gt;", '>'},
            {"&quot;", '"'},
            {"&apos;", '\''},
            {"&amp;", '&'}
    };

    size_t i = 0;  // 读指针
    size_t j = 0;  // 写指针

    while (i < content.size()) {
        if (content[i] == '&') {
            bool replaced = false;
            for (const auto& [entity, ch] : entities) {
                if (i + entity.size() <= content.size() &&
                        content.compare(i, entity.size(), entity.data(), entity.size()) == 0) {
                    content[j++] = ch;
                    i += entity.size();
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                content[j++] = content[i++];
            }
        } else {
            content[j++] = content[i++];
        }
    }
    content.resize(j);
}


/***
 * 强化版本的 对字符串中的HTML特殊标志符号, 非 ASCII Unicode 实体, 无分号实体、畸形数字实体、超长实体、嵌套实体
 * 进行解析,得到正常的字符串
 * @param content
 */
void string_ext::unescape_html_power(std::string &content) {
    if (content.empty()) return;

    auto write_utf8 = [](uint32_t code, char (&buf)[4]) -> int {
        if (code == 0) return 0;                       // 拒绝空字符
        if ((code >= 0xD800 && code <= 0xDFFF) || code >= 0x110000) return 0;
        if (code < 0x80) {
            buf[0] = static_cast<char>(code);
            return 1;
        } else if (code < 0x800) {
            buf[0] = static_cast<char>(0xC0 | (code >> 6));
            buf[1] = static_cast<char>(0x80 | (code & 0x3F));
            return 2;
        } else if (code < 0x10000) {
            buf[0] = static_cast<char>(0xE0 | (code >> 12));
            buf[1] = static_cast<char>(0x80 | ((code >> 6) & 0x3F));
            buf[2] = static_cast<char>(0x80 | (code & 0x3F));
            return 3;
        } else { // code <= 0x10FFFF
            buf[0] = static_cast<char>(0xF0 | (code >> 18));
            buf[1] = static_cast<char>(0x80 | ((code >> 12) & 0x3F));
            buf[2] = static_cast<char>(0x80 | ((code >> 6) & 0x3F));
            buf[3] = static_cast<char>(0x80 | (code & 0x3F));
            return 4;
        }
    };

    static constexpr struct EntityMap {
        std::string_view name;
        uint32_t code_point;
    } entity_map[] = {
            {"lt", '<'}, {"gt", '>'}, {"quot", '"'}, {"apos", '\''}, {"amp", '&'},
            {"nbsp", 0x00A0}, {"copy", 0x00A9}, {"reg", 0x00AE}, {"euro", 0x20AC}
    };

    size_t i = 0, j = 0;
    const size_t max_entity_length = 32;
    char utf8_buf[4];

    while (i < content.size()) {
        if (content[i] == '&') {
            size_t limit = std::min(content.size(), i + max_entity_length + 1);
            size_t end = std::string::npos;
            for (size_t k = i + 1; k < limit; ++k) {
                if (content[k] == ';') {
                    end = k;
                    break;
                }
            }
            if (end != std::string::npos) {
                std::string_view entity(&content[i + 1], end - i - 1);
                bool replaced = false;

                // 数字实体
                if (!entity.empty() && entity[0] == '#') {
                    if (entity.size() >= 2) {
                        uint32_t code = 0;
                        const char* start = entity.data() + 1;
                        size_t len = entity.size() - 1;
                        int base = 10;
                        if (entity[1] == 'x' || entity[1] == 'X') {
                            if (entity.size() >= 3) {
                                start += 1;
                                len -= 1;
                                base = 16;
                            }
                        }
                        if (len > 0) {
                            auto result = std::from_chars(start, start + len, code, base);
                            if (result.ec == std::errc() && result.ptr == start + len) {
                                int bytes = write_utf8(code, utf8_buf);
                                if (bytes > 0) {
                                    for (int n = 0; n < bytes; ++n) content[j++] = utf8_buf[n];
                                    i = end + 1;
                                    replaced = true;
                                }
                            }
                        }
                    }
                }
                    // 命名实体
                else {
                    for (const auto& [name, cp] : entity_map) {
                        if (entity.size() == name.size()) {
                            bool match = true;
                            for (size_t k = 0; k < name.size(); ++k) {
                                char c = static_cast<char>(std::tolower(static_cast<unsigned char>(entity[k])));
                                if (c != name[k]) {
                                    match = false;
                                    break;
                                }
                            }
                            if (match) {
                                int bytes = write_utf8(cp, utf8_buf);
                                for (int n = 0; n < bytes; ++n) content[j++] = utf8_buf[n];
                                i = end + 1;
                                replaced = true;
                                break;
                            }
                        }
                    }
                }
                if (replaced) continue;
            }
        }
        content[j++] = content[i++];
    }
    content.resize(j);
}


static size_t stripTagsAndCountWords(std::string_view html, size_t &picCount, size_t &charCount) {
    std::string text;
    text.reserve(html.size());
    bool inTag = false;
    for (size_t i = 0; i < html.size(); i++) {
        if (html[i] == '<') {
            if (i + 3 < html.size() &&
                std::tolower(static_cast<unsigned char>(html[i+1])) == 'i' &&
                std::tolower(static_cast<unsigned char>(html[i+2])) == 'm' &&
                std::tolower(static_cast<unsigned char>(html[i+3])) == 'g') {
                picCount++;
                i += 3;
            } else if (i + 5 < html.size() &&
                       std::tolower(static_cast<unsigned char>(html[i+1])) == 'i' &&
                       std::tolower(static_cast<unsigned char>(html[i+2])) == 'm' &&
                       std::tolower(static_cast<unsigned char>(html[i+3])) == 'a' &&
                       std::tolower(static_cast<unsigned char>(html[i+4])) == 'g' &&
                       std::tolower(static_cast<unsigned char>(html[i+5])) == 'e') {
                picCount++;
                i += 5;
            }
            inTag = true;
            continue;
        }
        if (html[i] == '>') { inTag = false; continue; }
        if (!inTag) text += html[i];
    }
    text = string_ext::cleanStr(text);
    if (!text.empty()) {
        charCount = string_ext::utf8Count(text);
    }
    return picCount + charCount;
}

size_t string_ext::count_text_pic_from_html(/*in*/std::string &rawHtml,
        /*in*/std::string &startAnchorId,/*in*/std::string &endAnchorId,
        /*out*/size_t &charCount,/*out*/size_t &picCount) {

    size_t bodyTagPos = ci_find(rawHtml, "<body");
    if (bodyTagPos == std::string::npos) {
        LOGE("%s: no <body> tag found in HTML", __func__);
        return 0;
    }
    size_t bodyOpenEnd = rawHtml.find('>', bodyTagPos);
    if (bodyOpenEnd == std::string::npos) {
        LOGE("%s: unclosed <body> tag", __func__);
        return 0;
    }
    size_t bodyContentStart = bodyOpenEnd + 1;
    size_t bodyClosePos = ci_rfind(rawHtml, "</body>");
    size_t bodyEnd = (bodyClosePos != std::string::npos) ? bodyClosePos : rawHtml.size();
    if (bodyContentStart >= bodyEnd) {
        LOGE("%s: empty body", __func__);
        return 0;
    }

    size_t segStart;
    if (startAnchorId.empty()) {
        segStart = bodyContentStart;
    } else {
        std::string searchPattern = "id=\"" + startAnchorId + "\"";
        size_t pos = rawHtml.find(searchPattern, bodyContentStart);
        if (pos == std::string::npos || pos >= bodyEnd) {
            searchPattern = "id='" + startAnchorId + "'";
            pos = rawHtml.find(searchPattern, bodyContentStart);
        }
        if (pos == std::string::npos || pos >= bodyEnd) {
            LOGE("%s: start anchor [%s] not found in body", __func__, startAnchorId.c_str());
            return 0;
        }
        size_t tagStart = rawHtml.rfind('<', pos);
        if (tagStart == std::string::npos || tagStart <= bodyContentStart) {
            segStart = bodyContentStart;
        } else {
            segStart = tagStart;
        }
    }

    size_t segEnd;
    if (endAnchorId.empty()) {
        segEnd = bodyEnd;
    } else {
        std::string searchPattern = "id=\"" + endAnchorId + "\"";
        size_t pos = rawHtml.find(searchPattern, segStart);
        if (pos == std::string::npos || pos >= bodyEnd) {
            searchPattern = "id='" + endAnchorId + "'";
            pos = rawHtml.find(searchPattern, segStart);
        }
        if (pos == std::string::npos || pos >= bodyEnd) {
            LOGE("%s: end anchor [%s] not found in body", __func__, endAnchorId.c_str());
            return 0;
        }
        size_t tagStart = rawHtml.rfind('<', pos);
        if (tagStart != std::string::npos && tagStart > segStart) {
            segEnd = tagStart;
        } else {
            segEnd = pos;
        }
    }

    if (segStart >= segEnd) {
        LOGE("%s: empty segment for (segStart=%zu, segEnd=%zu)",
             __func__, segStart, segEnd);
        return 0;
    }
    std::string_view segment(rawHtml.data() + segStart, segEnd - segStart);
    size_t totalCount = stripTagsAndCountWords(segment, picCount, charCount);
    LOGD("%s: wordCount[%zu], picCount[%zu] ", __func__, charCount, picCount);
    return totalCount;
}

size_t string_ext::count_text_pic_batch(const std::string &rawHtml,
        const std::vector<std::string> &anchors,
        std::vector<std::pair<size_t, size_t>> &counts) {

    if (anchors.empty()) {
        return 0;
    }

    size_t bodyTagPos = ci_find(rawHtml, "<body");
    if (bodyTagPos == std::string::npos) {
        LOGE("%s: no <body> tag found", __func__);
        return 0;
    }
    size_t bodyOpenEnd = rawHtml.find('>', bodyTagPos);
    if (bodyOpenEnd == std::string::npos) {
        LOGE("%s: unclosed <body> tag", __func__);
        return 0;
    }
    size_t bodyContentStart = bodyOpenEnd + 1;

    size_t bodyClosePos = ci_rfind(rawHtml, "</body>");
    size_t bodyEnd = (bodyClosePos != std::string::npos) ? bodyClosePos : rawHtml.size();

    if (bodyContentStart >= bodyEnd) {
        LOGE("%s: empty body", __func__);
        return 0;
    }

    std::vector<std::string> idValues;
    std::vector<size_t> idPositions;
    for (size_t i = bodyContentStart; i < bodyEnd;) {
        if (i + 3 >= bodyEnd) break;
        size_t idAttrPos = rawHtml.find("id=", i);
        if (idAttrPos == std::string::npos || idAttrPos >= bodyEnd) break;
        if (idAttrPos + 4 >= bodyEnd) break;

        char quote = rawHtml[idAttrPos + 3];
        if (quote == '"' || quote == '\'') {
            size_t valueStart = idAttrPos + 4;
            size_t valueEnd = rawHtml.find(quote, valueStart);
            if (valueEnd != std::string::npos && valueEnd < bodyEnd) {
                std::string idVal = rawHtml.substr(valueStart, valueEnd - valueStart);
                size_t elementStart = rawHtml.rfind('<', idAttrPos);
                if (elementStart == std::string::npos || elementStart < bodyContentStart) {
                    elementStart = bodyContentStart;
                }
                idValues.push_back(idVal);
                idPositions.push_back(elementStart);
                i = valueEnd + 1;
            } else {
                i = idAttrPos + 4;
            }
        } else {
            i = idAttrPos + 3;
        }
    }

    std::unordered_map<std::string, size_t> idMap;
    idMap.reserve(idValues.size());
    for (size_t j = 0; j < idValues.size(); j++) {
        idMap.emplace(idValues[j], idPositions[j]);
    }

    std::vector<size_t> segStarts(anchors.size(), bodyContentStart);
    for (size_t ch = 0; ch < anchors.size(); ch++) {
        if (anchors[ch].empty()) continue;
        auto it = idMap.find(anchors[ch]);
        if (it != idMap.end()) {
            segStarts[ch] = it->second;
        } else {
            LOGE("%s: anchor [%s] not found in body", __func__, anchors[ch].c_str());
        }
    }

    size_t total = 0;
    for (size_t ch = 0; ch < anchors.size(); ch++) {
        size_t segStart = segStarts[ch];
        size_t segEnd = (ch + 1 < anchors.size()) ? segStarts[ch + 1] : bodyEnd;

        if (segStart >= segEnd) {
            counts.emplace_back(0, 0);
            LOGD("%s: ch[%zu] empty segment [%zu, %zu)", __func__, ch, segStart, segEnd);
            continue;
        }

        std::string_view segment(rawHtml.data() + segStart, segEnd - segStart);
        size_t picCount = 0, charCount = 0;
        stripTagsAndCountWords(segment, picCount, charCount);
        counts.emplace_back(charCount, picCount);
        total += charCount + picCount;
        LOGD("%s: ch[%zu] anchor=[%s] charCount[%zu] picCount[%zu]",
             __func__, ch, anchors[ch].c_str(), charCount, picCount);
    }

    return total;
}

size_t string_ext::stripTagsAndCountBody(const std::string &html) {
    // 定位 <body>...</body>
    // 注意：虚拟切分场景下，调用方传入的是 extractBodyContent 截取的 body 内部片段，
    // 不含 <body> 标签本身。此时把整个输入当作 body 内容统计（而非返回 0）。
    size_t bodyTagPos = ci_find(html, "<body");
    size_t start, end;
    if (bodyTagPos == std::string::npos) {
        // 无 <body> 标签：整个输入视为 body 内容（虚拟切分段、纯片段场景）
        start = 0;
        end = html.size();
    } else {
        size_t bodyOpenEnd = html.find('>', bodyTagPos);
        if (bodyOpenEnd == std::string::npos) return 0;
        start = bodyOpenEnd + 1;
        size_t bodyClosePos = ci_rfind(html, "</body>");
        end = (bodyClosePos != std::string::npos) ? bodyClosePos : html.size();
    }
    if (start >= end) return 0;

    // 去标签 + cleanStr + utf8Count
    std::string text;
    text.reserve(end - start);
    bool inTag = false;
    for (size_t i = start; i < end; i++) {
        if (html[i] == '<') { inTag = true; continue; }
        if (html[i] == '>') { inTag = false; continue; }
        if (!inTag) text += html[i];
    }
    text = string_ext::cleanStr(text);
    if (text.empty()) return 0;
    return string_ext::utf8Count(text);
}

size_t string_ext::countImages(const std::string &html) {
    size_t count = 0, pos = 0;
    while ((pos = html.find("<img ", pos)) != std::string::npos) { count++; pos += 5; }
    pos = 0;
    while ((pos = html.find("<image ", pos)) != std::string::npos) { count++; pos += 7; }
    return count;
}


/***
 * 从 HTML 提取 <body>...</body> 内层内容
 */
std::string string_ext::extractBodyContent(const std::string &rawHtml) {
    //body 开始标签的开始位置
    size_t bodyTagPos = rawHtml.find("<body");
    if (bodyTagPos == std::string::npos) return rawHtml;
    //body 开始标签的结束位置
    size_t bodyOpenEnd = rawHtml.find('>', bodyTagPos);
    if (bodyOpenEnd == std::string::npos) return rawHtml;

    //body 内的开始
    size_t bodyStart = bodyOpenEnd + 1;
    size_t bodyClosePos = rawHtml.rfind("</body>");
    //body 结束标签的开始位置 ，也就是body内的结束位置
    size_t bodyEnd = (bodyClosePos != std::string::npos) ? bodyClosePos : rawHtml.size();
    if (bodyStart >= bodyEnd) return "";
    return rawHtml.substr(bodyStart, bodyEnd - bodyStart);
}

/****
 * 从 HTML 提取 CSS
 * @param rawHtml
 * @param outExtHrefs
 * @return
 */
std::string string_ext::extractCssByString(const std::string &rawHtml,
                                          std::vector<std::string> &outExtHrefs) {
    std::string css;  // 仅内联 <style> 内容
    // ── 性能：CSS（<style>/<link rel=stylesheet>）按 HTML/XHTML 规范只在 <head> 内出现。
    //   对大章节（正文 10MB+），全文 findCaseInsensitive 扫描（每字符一次 tolower）会耗时
    //   近 2 秒，其中绝大部分是无效的正文扫描。这里把搜索范围限制到 <head>...</head> 区域
    //   （或 <body> 之前的部分），head 通常 <10KB，扫描量从 10MB 降到 KB 级。
    //   回退策略：找不到 </head>/<body>（畸形文档）则退回全文扫描，保持原行为不丢功能。 ──
    auto _t_css0 = std::chrono::high_resolution_clock::now();
    size_t searchLimit = rawHtml.size();  // 默认全文（回退）
    do {
        size_t headEnd = findCaseInsensitive(rawHtml, "</head>", 0);
        if (headEnd != std::string::npos) {
            searchLimit = headEnd + 7;  // 含 </head> 本身
            break;
        }
        size_t bodyPos = findCaseInsensitive(rawHtml, "<body", 0);
        if (bodyPos != std::string::npos) {
            searchLimit = bodyPos;  // <body> 之前
            break;
        }
        // 都找不到 → 保持全文（畸形 HTML 回退）
    } while (false);

    // 1. 内联 <style> 块（大小写不敏感搜索 <style ...> ... </style>）
    size_t pos = 0;
    while ((pos = findCaseInsensitive(rawHtml, "<style", pos, searchLimit)) != std::string::npos) {
        size_t close = findCaseInsensitive(rawHtml, "</style>", pos);
        if (close == std::string::npos) break;
        size_t gt = rawHtml.find('>', pos);
        if (gt != std::string::npos && gt < close) {
            css += rawHtml.substr(gt + 1, close - (gt + 1));
        }
        pos = close + 8;
    }
    // 2. 外链 <link rel="stylesheet">：字符串搜索 <link ...>，宽松匹配 href 与 rel。
    //    v9 起只提取 href 放入 outExtHrefs，不再在切分期从 zip 加载 CSS 内容——
    //    切分期 cssSrc 尚未从 manifest 填充（getChapters 早于 parse_css_list），
    //    原加载逻辑因 cssSrc.empty() 永远短路，外链 CSS 被静默丢弃。
    //    改为：href 随缓存落盘，渲染期由 loadExternalCss 从 manifest 重新加载，
    //    与真实章（type==0）流程一致。
    pos = 0;
    while ((pos = findCaseInsensitive(rawHtml, "<link", pos, searchLimit)) != std::string::npos) {
        size_t gt = rawHtml.find('>', pos);
        if (gt == std::string::npos) break;
        std::string tag = rawHtml.substr(pos, gt - pos + 1);  // 整个 <link ...>
        std::string tagLower = string_ext::to_lower(tag);
        bool isStylesheet = tagLower.find("stylesheet") != std::string::npos;
        if (isStylesheet) {
            // 提取 href 值（容错单双引号）
            size_t hrefPos = tagLower.find("href");
            if (hrefPos != std::string::npos) {
                size_t eq = tag.find('=', hrefPos);
                if (eq != std::string::npos) {
                    size_t vStart = tag.find_first_of("'\"", eq);
                    if (vStart != std::string::npos) {
                        char quote = tag[vStart];
                        size_t vEnd = tag.find(quote, vStart + 1);
                        if (vEnd != std::string::npos) {
                            std::string href = tag.substr(vStart + 1, vEnd - vStart - 1);
                            href = string_ext::base_url_decode(href);
                            LOGD("%s:bookload:extractCssByString:href=%s", __func__, href.c_str());
                            // 仅存路径（已 decode），渲染期再从 manifest 加载
                            outExtHrefs.push_back(href);
                        }
                    }
                }
            }
        }
        pos = gt + 1;
    }
    auto _t_css1 = std::chrono::high_resolution_clock::now();
    LOGI("%s:perf: extractCssByString=%lldms, cssSize=%zu, extHrefs=%zu, rawHtmlSize=%zu, searchLimit=%zu",
         __func__,
         std::chrono::duration_cast<std::chrono::milliseconds>(_t_css1 - _t_css0).count(),
         css.size(), outExtHrefs.size(), rawHtml.size(), searchLimit);
    return css;
}

/***
 * 大小写不敏感查找子串（单遍扫描，O(n)）。
 * 避免在数十 MB 内容上反复 to_lower 导致 O(n²)。
 * @param endPos 搜索上限（不含）。默认 npos 表示扫到末尾。传入 < bodyPos 可把扫描
 *               限制在 <head> 区域，对大章节正文（10MB+）可从全文扫描降到 head 扫描（<10KB）。
 */
size_t string_ext::findCaseInsensitive(const std::string &haystack,
                                  const std::string &needle,
                                  size_t startPos,
                                  size_t endPos) {
    if (needle.empty() || startPos >= haystack.size()) return std::string::npos;
    if (needle.size() > haystack.size() - startPos) return std::string::npos;
    if (endPos > haystack.size()) endPos = haystack.size();
    if (endPos < startPos) return std::string::npos;
    std::string needleLower;
    needleLower.reserve(needle.size());
    for (char c : needle) needleLower.push_back((char)tolower((unsigned char)c));
    // 滑动窗口比较：仅对窗口内字符 to_lower，不对全文做拷贝
    // endPos 限制右边界：窗口 [i, i+needleLower.size()) 不得越过 endPos
    for (size_t i = startPos; i + needleLower.size() <= endPos; ++i) {
        size_t j = 0;
        for (; j < needleLower.size(); ++j) {
            if (tolower((unsigned char)haystack[i + j]) != (unsigned char)needleLower[j]) break;
        }
        if (j == needleLower.size()) return i;
    }
    return std::string::npos;
}