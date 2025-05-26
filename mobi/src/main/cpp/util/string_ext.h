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

std::vector<std::string> split(const std::string &s, char delimiter);

inline void ltrim(std::string &s);

inline void rtrim(std::string &s);

inline void trim(std::string &s);

inline std::string trim_copy(std::string s);

bool endsWith(const std::string &str, const std::string &suffix);

bool endsWithIgnoreCase(std::string str, std::string suffix);

bool startWith(const std::string& str, const std::string& prefix);

/***
 * 统计utf8的字符数， 常规的std::string的size，lenght字符有问题
 * @param utf8_str
 * @return
 */
size_t utf8Count(const std::string& utf8_str);

/****
 * 创建随机的UUID
 * @return
 */
std::string generate_uuid();

int toInt(std::string value);

/****
 * 替换文件的后缀名
 * @param filePath
 * @param newExt
 * @return
 */
std::string replaceExtension(const std::string &filePath, const std::string &newExt);


#endif //SIMPLEREADER2_STRING_EXT_H
