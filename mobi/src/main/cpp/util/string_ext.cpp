//
// Created by wxn on 2025/4/14.
//

#include "string_ext.h"
#include <string>
#include <vector>
#include <cctype>
#include<algorithm>

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
inline void ltrim(std::string &s) {
    s.erase(s.begin(), std::find_if(s.begin(), s.end(), [](unsigned char ch) {
        return !std::isspace(ch);
    }));
}

//trim from end(in-place)
inline void rtrim(std::string &s) {
    s.erase(std::find_if(s.rbegin(), s.rend(), [](unsigned char ch) {
        return !std::isspace(ch);
    }).base(), s.end());
}

//trim from both ends(in-place)
inline void trim(std::string &s) {
    ltrim(s);
    rtrim(s);
}

//返回新字符串的版本(非原地修改)
inline std::string trim_copy(std::string s) {
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