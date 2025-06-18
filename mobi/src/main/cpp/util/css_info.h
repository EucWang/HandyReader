//
// Created by MAC on 2025/6/18.
//

#ifndef U_READER2_CSS_INFO_H
#define U_READER2_CSS_INFO_H

#include <string>
#include <vector>

typedef struct RuleData_ {
    std::string name;
    std::string value;
} RuleData;

typedef struct CssInfo_ {
    std::string identifier;
    int weight;
    bool isBaseSelector;
    std::vector<RuleData> ruleDatas;
} CssInfo;

#endif //U_READER2_CSS_INFO_H
