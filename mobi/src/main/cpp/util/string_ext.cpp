//
// Created by wxn on 2025/4/14.
//

#include "string_ext.h"
#include <string>
#include <vector>
#include <cctype>
#include<algorithm>

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

bool startWith(const std::string &str, const std::string &prefix) {
    if (prefix.empty()) return true;
    if (str.size() < prefix.size()) return false;
    return str.substr(0, prefix.size()) == prefix;
}

/***
 * 统计utf8的字符数， 常规的std::string的size，lenght字符有问题
 * @param utf8_str
 * @return
 */
size_t utf8Count(const std::string &utf8_str) {
    try {
        return utf8::distance(utf8_str.begin(), utf8_str.end());
    } catch (utf8::invalid_utf8 &e) {
        LOGE("%s:failed invalid utf8[%s], %s", __func__, utf8_str.c_str(), e.what());
        return 0;
    } catch (utf8::not_enough_room &e) {
        LOGE("%s:failed not enought room utf8[%s], %s", __func__, utf8_str.c_str(), e.what());
        return 0;
    }
}

/****
 * 创建随机的UUID
 * @return
 */
std::string generate_uuid() {
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

int toInt(std::string value) {
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
std::string replaceExtension(const std::string &filePath, const std::string &newExt) {
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

std::vector<std::string> split(const std::string &s, char delimiter) {
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
void ltrim(std::string &s) {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
}

//trim from end(in-place)
void rtrim(std::string &s) {
    s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), s.end());
}

//trim from both ends(in-place)
void trim(std::string &s) {
    ltrim(s);
    rtrim(s);
}

//返回新字符串的版本(非原地修改)
std::string trim_copy(std::string s) {
    trim(s);
    return s;
}

bool endsWith(const std::string &str, const std::string &suffix) {
    return str.size() >= suffix.size() &&
           std::equal(suffix.rbegin(), suffix.rend(), str.rbegin());
}

bool endsWithIgnoreCase(std::string str, std::string suffix) {
    if (suffix.size() > str.size())return false;
    std::transform(str.begin(), str.end(), str.begin(), tolower);
    std::transform(suffix.begin(), suffix.end(), suffix.begin(), tolower);
    return str.compare(str.size() - suffix.size(), suffix.size(), suffix) == 0;
}

void replace_all(std::string &input, std::string &old_str, std::string &new_str) {
    size_t pos = 0;
    while ((pos = input.find(old_str, pos)) != std::string::npos) {
        input.replace(pos, old_str.length(), new_str);
        pos += new_str.length();
    }
}


std::string &cleanStr(const std::string &str) {
    std::string ret = str;
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
    ret = std::regex_replace(ret, regex, my_empty_str);
    trim(ret);
    return ret;
}

/***
 * 判断是不是纯数字
 * @param value
 * @return
 */
bool is_number(std::string &str) {
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