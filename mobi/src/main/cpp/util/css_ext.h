//
// Created by MAC on 2025/6/19.
//

#ifndef U_READER2_CSS_EXT_H
#define U_READER2_CSS_EXT_H

#include "string_ext.h"
#include "../../cssparser/CSSParser/CSSParser.hpp"
#include <string>
#include "css_info.h"
#include <vector>

class css_ext {
public:

    /****
     * 根据 传入的 cssClasses, cssTags, cssIds 查找对应的Css数据,并保存到cssInfos中输出
     * @param css_data
     * @param cssClasses
     * @param cssTags
     * @param cssIds
     * @param cssInfos
     */
    static void query_css(std::string &css_data,
                          std::vector<std::string> &cssClasses,
                          std::vector<std::string> &cssTags,
                          std::vector<std::string> &cssIds,
                          std::vector<CssInfo> &cssInfos);

    /****
     * 解析css 数据, 并保存到cssInfos中输出
     * @param css_data
     * @param cssInfos
     */
    static void parse_css(std::string &css_data,
                            std::vector<CssInfo> &cssInfos);

    /****
     * v4.0: 对累积后的 cssInfos 做原地 sort + deduplicate
     * - 按 weight 升序排序(保证高 specificity 在后,后续 last-wins 时高获胜)
     * - 按 type+identifier 分组合并 ruleDatas(同名属性 last-wins)
     * 调用时机:handle_tags 入口,parse_css 累积完内联+外链 CSS 之后
     * @param cssInfos 输入输出参数,原地修改
     */
    static void sort_and_deduplicate_inplace(std::vector<CssInfo> &cssInfos);

    /****
     * v4.0: 把 rule_datas 合并到 params 字符串(last-wins 语义)
     * - 前置条件:rule_datas 来自按 weight 升序的 cssInfos 命中
     * - 输出:params 字符串里同名属性只出现一次,值是高 specificity 的
     * - v4.0:不再显式丢弃 background,native 透传所有 CSS 属性给 Kotlin
     * @param params 原始 params 字符串(如 "class=foo&id=bar")
     * @param rule_datas 命中规则的 rule data 列表
     * @return 合并后的新 params 字符串
     */
    static std::string apply_css_to_params(const std::string &params,
                                           const std::vector<RuleData> &rule_datas);
};


#endif //U_READER2_CSS_EXT_H
