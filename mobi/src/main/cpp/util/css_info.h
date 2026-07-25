//
// Created by MAC on 2025/6/18.
//

#ifndef U_READER2_CSS_INFO_H
#define U_READER2_CSS_INFO_H

#include <string>
#include <vector>

const std::string CssInfo_Type_Class = "class";
const std::string CssInfo_Type_Tag = "tag";
const std::string CssInfo_Type_Id = "id";

typedef struct RuleData_ {
    std::string name;
    std::string value;
} RuleData;


typedef struct CssInfo_ {
    std::string identifier;
    int weight;             // v4.0: 已启用——sort_and_deduplicate_inplace 按 weight 升序排序保证 specificity
    bool isBaseSelector;    // 已弃用:展开后叶子永真;保留字段避免 ABI 变动,待后续重构清理
    std::vector<RuleData> ruleDatas;
    std::string type;   //Css 类型， 取值： class: 类选择器, tag: 元素选择器, id: ID选择器
} CssInfo;

#endif //U_READER2_CSS_INFO_H
