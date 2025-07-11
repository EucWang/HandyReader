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

    /****
     * 基础的URL解码，支持ASCII 和空格
     * @param str
     * @return
     */
    static std::string base_url_decode(const std::string &str);
};
#endif //SIMPLEREADER2_STRING_EXT_H
