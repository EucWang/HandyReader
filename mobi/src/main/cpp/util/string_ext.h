//
// Created by wxn on 2025/4/14.
//

#ifndef SIMPLEREADER2_STRING_EXT_H
#define SIMPLEREADER2_STRING_EXT_H

#ifdef __cplusplus
extern "C" {
#include <ctype.h>
};
#endif

#include <iomanip>
#include <sstream>
#include <random>
#include <iostream>
#include <filesystem> // C++17 标准库
#include "log.h"
#include <string>
#include "utf8.h"
#include <regex>
#include <jni.h>
#include <list>
#include <cctype>
#include <string_view>
#include <charconv>
#include <array>
#include <algorithm>

class string_ext {

public:
    static std::vector<std::string> split(const std::string &s, char delimiter);

    static void ltrim(std::string &s);

    static void rtrim(std::string &s);

    static void trim(std::string &s);

    static std::string trim_copy(std::string s);

    static bool endsWith(const std::string &str, const std::string &suffix);

    static bool endsWithIgnoreCase(std::string str, std::string suffix);

    static bool startWith(const std::string& str, const std::string& prefix);

    static void removeHtmlTagWrap(std::string &page_css_style, const std::string &tag_name);

    /**
     * 移除字符串中所有 C 风格注释
     */
    static void remove_c_style_comments(std::string& input);

    /***
     * 统计utf8的字符数， 常规的std::string的size，lenght字符有问题
     * @param utf8_str
     * @return
     */
    static size_t utf8Count(const std::string& utf8_str);
    
    /****
     * 创建随机的UUID
     * @return
     */
    static std::string generate_uuid();

    static int toInt(std::string value);
    
    /***
     * 判断是不是纯数字
     * @param value
     * @return
     */
    static bool is_number(std::string &value);
    
    /****
     * 替换文件的后缀名
     * @param filePath
     * @param newExt
     * @return
     */
    static std::string replaceExtension(const std::string &filePath, const std::string &newExt);

    static void replace_all(std::string &input, std::string &old_str, std::string &new_str);

    static std::string cleanStr(const std::string &str);

    /***
     * 移除字符串中所有 <...> HTML 标签，返回纯文本（不做 cleanStr/计数）
     * 用于从含标签的章节标题容器中提取纯标题文本
     */
    static std::string stripTags(const std::string &html);

    /****
     * 基础的URL解码，支持ASCII 和空格
     * @param str
     * @return
     */
    static std::string base_url_decode(const std::string &str);

    static std::string to_lower(const std::string &str);

    /***
     * 将字符串中的HTML特殊标志符号转换成正常的显示字符
     * @param content
     */
    static void unescape_html(std::string &content);

    /***
     * 强化版本的 对字符串中的HTML特殊标志符号, 非 ASCII Unicode 实体, 无分号实体、畸形数字实体、超长实体、嵌套实体
     * 进行解析,得到正常的字符串
     * @param content
     */
    static void unescape_html_power(std::string &content);

    /***
     * 从HTML中提取文本和图片数量
     * @param rawHtml
     * @param startAnchorId
     * @param endAnchorId
     * @param charCount
     * @param picCount
     * @return
     */
    static size_t count_text_pic_from_html(/*in*/std::string &rawHtml,
            /*in*/std::string &startAnchorId,/*in*/std::string &endAnchorId,
            /*out*/size_t &charCount,/*out*/size_t &picCount);

    /***
     * 批量统计一个HTML资源中多个章节的文本和图片数量
     * 单次扫描提取所有锚点位置，按 anchors 顺序切分章节段落并分别统计
     * @param rawHtml      [in]  原始HTML字符串
     * @param anchors      [in]  每个章节的起始锚点ID，按章节顺序排列，首章节可能为空
     * @param counts       [out] 每个章节的 {charCount, picCount}
     * @return 总计 (charCount + picCount)，失败返回 0
     */
    static size_t count_text_pic_batch(const std::string &rawHtml,
            const std::vector<std::string> &anchors,
            std::vector<std::pair<size_t, size_t>> &counts);

    /***
     * 统计 HTML 中 <body> 内容的字数（去标签 + cleanStr + utf8Count）
     * 排除 <head>/<style>/<script> 等非正文文本，用于虚拟切分章字数统计
     * @param html 含 <html><head>...</head><body>...</body></html> 的完整片段
     * @return body 内纯文本字符数（UTF8 计数）
     */
    static size_t stripTagsAndCountBody(const std::string &html);

    /***
     * 统计 HTML 中 <img 和 <image 标签数量
     * @param html
     * @return 图片数量
     */
    static size_t countImages(const std::string &html);

    /***
     * 从 HTML 提取 <body>...</body> 内层内容
     */
    static std::string extractBodyContent(const std::string &rawHtml);


    /****
     * 从 HTML 提取 CSS
     * @param rawHtml
     * @param outExtHrefs
     * @return
     */
    static std::string extractCssByString(const std::string &rawHtml,
                                               std::vector<std::string> &outExtHrefs);

    /***
     * 大小写不敏感查找子串（单遍扫描，O(n)）。
     * 避免在数十 MB 内容上反复 to_lower 导致 O(n²)。
     * @param endPos 搜索上限（不含）。默认 npos 表示扫到末尾。传入 < bodyPos 可把扫描
     *               限制在 <head> 区域，对大章节正文（10MB+）可从全文扫描降到 head 扫描（<10KB）。
     */
    static size_t findCaseInsensitive(const std::string &haystack,
                                           const std::string &needle,
                                           size_t startPos,
                                           size_t endPos = std::string::npos);

    /***
     * 剥离最外层 XML CDATA 包裹标记 <![CDATA[ ... ]]>（大小写不敏感，成对才剥，只剥一层）。
     * tidy 的 XML 输出(TidyXmlOut=yes)会给 <style> 内容加 CDATA 包裹，
     * 源 XHTML 也可能自带；CSS 词法器遇 '<' 即终止，残留标记会导致整段规则静默丢失。
     * @param text
     */
    static void removeCDataWrap(std::string &text);
};
#endif //SIMPLEREADER2_STRING_EXT_H
