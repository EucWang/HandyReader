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

#include <string>


std::vector<std::string> split(const std::string &s, char delimiter);

inline void ltrim(std::string &s);

inline void rtrim(std::string &s);

inline void trim(std::string &s);

inline std::string trim_copy(std::string s);

bool endsWith(const std::string &str, const std::string &suffix);

bool endsWithIgnoreCase(std::string str, std::string suffix);

#endif //SIMPLEREADER2_STRING_EXT_H
