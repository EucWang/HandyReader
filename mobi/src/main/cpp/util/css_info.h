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
    // v4.0.1 修复:sourceOrder = emit_css_infos_iterative 中该 CssInfo 被追加到 cssInfos 的下标,
    // 即 CSS 源码出现顺序。用作 sort 的全序 tiebreaker(参见 sort_and_deduplicate_inplace),
    // 消除"同 weight 规则跨进程随机胜出"的非确定性 bug。
    // 必须放在末尾:css_ext.cpp:68 的位置初始化依赖字段顺序,加在中间会破坏现有初始化。
    size_t sourceOrder;
} CssInfo;

#endif //U_READER2_CSS_INFO_H
