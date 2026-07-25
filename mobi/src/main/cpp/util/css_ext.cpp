//
// Created by MAC on 2025/6/19.
//

#include "css_ext.h"
#include <stack>
#include <algorithm>
#include <map>
#include <set>
#include <utility>

// v4.0: 不递归展开 selector 树,改用显式栈实现循环(避免栈溢出风险)
// v4.0: 递归深度防御性上限(CSS 规范 selector 嵌套 ≤ 9 层,这里留足余量)
static const size_t EMIT_MAX_DEPTH = 32;

// v4.0:前向声明,parse_to_css_info 调用 parse_to_css_info_with_rules
CssInfo parse_to_css_info_with_rules(future::Selector *selector,
                                     const std::string &identifier,
                                     int type,
                                     const std::string &ruleData);

CssInfo parse_to_css_info(future::Selector *selector, std::string &identifier, int type) {
    return parse_to_css_info_with_rules(selector, identifier, type, selector->getRuleData());
}

CssInfo parse_to_css_info_with_rules(future::Selector *selector,
                                     const std::string &identifier,
                                     int type,
                                     const std::string &ruleData) {
    std::vector<std::string> datas = string_ext::split(ruleData, ';');
    std::vector<RuleData> params;
    if (!datas.empty()) {
        for (auto &data: datas) {
            string_ext::trim(data);
            if (string_ext::endsWith(data, "}")) {
                data = data.substr(0, data.size() - 1);
            }
            string_ext::trim(data);
            std::vector<std::string> kv = string_ext::split(data, ':');
            if (kv.size() == 2) {
                std::string k = string_ext::trim_copy(kv[0]);
                std::string v = string_ext::trim_copy(kv[1]);
                if (!k.empty() && !v.empty()) {
                    params.emplace_back(RuleData{k, v});
                }
            }
        }
    }
    std::string selectorType;
    switch (type) {
        case 0 : {
            selectorType = CssInfo_Type_Class;
            break;
        }
        case 1: {
            selectorType = CssInfo_Type_Tag;
            break;
        }
        case 2 : {
            selectorType = CssInfo_Type_Id;
            break;
        }
        default: {
            break;
        }
    }

    return CssInfo{identifier, selector->weight(), selector->isBaseSelector(), params, selectorType};
}

/****
 * v4.0: 循环展开 selector 树,把 base selector (Class/Type/Id) 注册为 CssInfo
 * - 用显式栈替代递归,避免栈溢出
 * - ruleData 从父级(GroupSelector/SequenceSelector/CombineSelector)向下透传
 * - CombineSelector 用 getCombineType() != NoCombine 判断(getBefore/getAfter 在未配置时返回 UB)
 * @param root 顶层 selector
 * @param rootRuleData 顶层 selector 的 ruleData(顶层 selector 通常已 setRuleData)
 * @param cssInfos 输出:累积 CssInfo 列表(不在内部去重,去重推迟到 handle_tags 入口)
 */
static void emit_css_infos_iterative(future::Selector *root,
                                     const std::string &rootRuleData,
                                     std::vector<CssInfo> &cssInfos) {
    if (root == nullptr) { return; }

    // 显式栈:pair<selector, parentRuleData>
    std::stack<std::pair<future::Selector *, std::string>> pending;
    pending.push({root, rootRuleData});

    while (!pending.empty()) {
        // v4.0 M3:防御性深度限制,循环版用栈大小判断
        if (pending.size() > EMIT_MAX_DEPTH) {
            LOGW("emit_css_infos: depth exceeds %zu, abort subtree", EMIT_MAX_DEPTH);
            break;
        }

        auto current = pending.top();
        pending.pop();
        future::Selector *selector = current.first;
        const std::string &parentRuleData = current.second;

        if (selector == nullptr) { continue; }

        // ruleData 优先用本层(如 GroupSelector 整体 setRuleData 过),否则用父级
        std::string ruleData = parentRuleData;
        const std::string &selfRule = selector->getRuleData();
        if (!selfRule.empty()) {
            ruleData = selfRule;
        }

        auto type = selector->getType();
        if (type == future::ClassSelector::SelectorType::ClassSelector) {
            auto *s = dynamic_cast<future::ClassSelector *>(selector);
            if (s) {
                cssInfos.emplace_back(parse_to_css_info_with_rules(s, s->getClassIdentifier(), 0, ruleData));
            }
        } else if (type == future::TypeSelector::SelectorType::TypeSelector) {
            auto *s = dynamic_cast<future::TypeSelector *>(selector);
            if (s) {
                cssInfos.emplace_back(parse_to_css_info_with_rules(s, s->getTagName(), 1, ruleData));
            }
        } else if (type == future::IdSelector::SelectorType::IDSelector) {
            auto *s = dynamic_cast<future::IdSelector *>(selector);
            if (s) {
                cssInfos.emplace_back(parse_to_css_info_with_rules(s, s->getIdIdentifier(), 2, ruleData));
            }
        } else if (type == future::Selector::SelectorGroup) {
            auto *group = dynamic_cast<future::GroupSelector *>(selector);
            if (group) {
                for (auto *child: group->getAllSelectors()) {
                    if (child != nullptr) {
                        pending.push({child, ruleData});
                    }
                }
            }
        } else if (type == future::Selector::SimpleSelectorSequence) {
            auto *seq = dynamic_cast<future::SequenceSelector *>(selector);
            if (seq) {
                for (auto *child: seq->getContrains()) {
                    if (child != nullptr) {
                        pending.push({child, ruleData});
                    }
                }
            }
        } else if (type == future::Selector::CombineSelector) {
            auto *combo = dynamic_cast<future::CombineSelector *>(selector);
            // v4.0 S1 修复:getBefore/getAfter 在未配置状态返回 UB(非 nullptr),
            // 必须先用 getCombineType() 判断是否有效配置,否则会解引用未初始化迭代器崩溃
            if (combo != nullptr && combo->getCombineType() != future::CombineSelector::NoCombine) {
                future::Selector *before = combo->getBefore();
                future::Selector *after = combo->getAfter();
                if (before != nullptr) {
                    pending.push({before, ruleData});
                }
                if (after != nullptr) {
                    pending.push({after, ruleData});
                }
            }
        }
        // 其它(UniversalSelector / AttributeSelector / PseudoSelector / SignSelector):
        // 当前业务不消费,静默跳过
    }
}

void css_ext::parse_css(std::string &css_data,
                        std::vector<CssInfo> &cssInfos) {
    if (css_data.empty()) {
        return;
    }
    future::CSSParser cssParser;
    bool ret = cssParser.parseByString(css_data);
    if (ret) {
        const std::set<future::Selector *> &set = cssParser.getSelectors();
        // v4.0:预留空间,避免多次 emplace_back 触发多次 reallocate
        cssInfos.reserve(cssInfos.size() + set.size() * 2);
        for (auto it = set.begin(); it != set.end(); it++) {
            // 顶层 selector 的 ruleData 已被 CSSParser.cpp:170/178 set 过,
            // 传空串作为初始 parentRuleData,emit_css_infos_iterative 内部会用 selector 自身的 ruleData
            emit_css_infos_iterative(*it, std::string(), cssInfos);
        }
    }
    // v4.0:不在 parse_css 内部做 deduplicate
    // 原因:parse_css 单次 invoke() 会被多次累积调用(内联 <style> + 外链 <link> CSS),
    // 跨调用的 deduplicate 必须在 handle_tags 入口做(对累积后的完整 cssInfos 一次性去重)
    // 同时移除原 LOGE("parse_css: ret=%d, ...") 调试日志
}

/****
 * v4.0: 对累积后的 cssInfos 做原地 sort + deduplicate
 * 调用时机:3 个 handle_tags 入口,parse_css 累积完内联+外链 CSS 之后
 */
void css_ext::sort_and_deduplicate_inplace(std::vector<CssInfo> &cssInfos) {
    if (cssInfos.empty()) {
        return;
    }

    // 1. 按 weight 升序排序(低 specificity 在前,高在后;后续 last-wins 时高获胜)
    std::sort(cssInfos.begin(), cssInfos.end(),
              [](const CssInfo &a, const CssInfo &b) { return a.weight < b.weight; });

    // 2. 按 type+identifier 分组聚合
    // 使用 map<key, vector<size_t>> 记录每组的索引
    std::map<std::string, std::vector<size_t>> groups;
    for (size_t i = 0; i < cssInfos.size(); i++) {
        std::string key = cssInfos[i].type + ":" + cssInfos[i].identifier;
        groups[key].push_back(i);
    }

    // 3. 对每组做 last-wins 合并(同名属性后写覆盖前写;因已按 weight 升序,高 weight 在后获胜)
    std::vector<CssInfo> merged;
    merged.reserve(cssInfos.size());
    std::set<size_t> consumed;  // 已被合并到其它元素的索引(保留每组首个索引位置)

    for (size_t i = 0; i < cssInfos.size(); i++) {
        if (consumed.count(i)) {
            continue;
        }
        std::string key = cssInfos[i].type + ":" + cssInfos[i].identifier;
        auto &groupIndices = groups[key];
        if (groupIndices.size() == 1) {
            // 单元素,无冲突,直接保留
            merged.push_back(cssInfos[i]);
        } else {
            // 多元素合并:last-wins 同名属性(用 vector<pair> 保留 cssInfos 顺序,避免字母序副作用)
            std::vector<std::pair<std::string, std::string>> lastKvs;
            for (size_t idx: groupIndices) {
                for (auto &rd: cssInfos[idx].ruleDatas) {
                    bool found = false;
                    for (auto &kv: lastKvs) {
                        if (kv.first == rd.name) {
                            kv.second = rd.value;   // 后写覆盖前写
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        lastKvs.emplace_back(rd.name, rd.value);
                    }
                }
                if (idx != i) {
                    consumed.insert(idx);
                }
            }
            // 合并后的 CssInfo:identifier/type 取首个,weight 取最高(最后一个,因已升序)
            CssInfo merged_info = cssInfos[i];
            merged_info.weight = cssInfos[groupIndices.back()].weight;
            merged_info.ruleDatas.clear();
            for (auto &kv: lastKvs) {
                merged_info.ruleDatas.emplace_back(RuleData{kv.first, kv.second});
            }
            merged.push_back(merged_info);
        }
    }

    cssInfos = std::move(merged);
}

/****
 * v4.0: 把 rule_datas 合并到 params 字符串(last-wins 语义)
 * - 前置条件:rule_datas 来自按 weight 升序的 cssInfos 命中
 * - 用 vector<pair> + 手动 last-wins 保留 cssInfos 遍历顺序(避免 std::map 字母序副作用)
 * - 不再显式 continue background(v4.0:native 透传所有 CSS 属性)
 */
std::string css_ext::apply_css_to_params(const std::string &params,
                                         const std::vector<RuleData> &rule_datas) {
    if (rule_datas.empty()) {
        return params;
    }

    // last-wins 合并(保留 cssInfos 顺序,避免字母序副作用)
    std::vector<std::pair<std::string, std::string>> lastKvs;
    for (auto &rd: rule_datas) {
        bool found = false;
        for (auto &kv: lastKvs) {
            if (kv.first == rd.name) {
                kv.second = rd.value;   // 后写覆盖前写
                found = true;
                break;
            }
        }
        if (!found) {
            lastKvs.emplace_back(rd.name, rd.value);
        }
    }

    std::stringstream ss;
    if (!params.empty()) {
        ss << params;
        ss << "&";
    }
    for (auto &kv: lastKvs) {
        ss << kv.first << "=" << kv.second << "&";
    }
    std::string result = ss.str();
    if (!result.empty() && result.back() == '&') {
        result = result.substr(0, result.length() - 1);
    }
    return result;
}

/****
 * 根据 传入的 cssClasses, cssTags, cssIds 查找对应的Css数据,并保存到cssInfos中输出
 * @param css_data
 * @param cssClasses
 * @param cssTags
 * @param cssIds
 * @param cssInfos
 */
void css_ext::query_css(std::string &css_data,
                        std::vector<std::string> &cssClasses,
                        std::vector<std::string> &cssTags,
                        std::vector<std::string> &cssIds,
                        std::vector<CssInfo> &cssInfos) {
    if (css_data.empty()) {
        return;
    }
    if (cssClasses.empty() && cssTags.empty() && cssIds.empty()) {
        return;
    }

    future::CSSParser cssParser;
    bool ret = cssParser.parseByString(css_data);
    if (ret) {
        const std::set<future::Selector *> &set = cssParser.getSelectors();
        for (auto it = set.begin(); it != set.end(); it++) {
            auto type = (*it)->getType();
            if (type == future::ClassSelector::SelectorType::ClassSelector && !cssClasses.empty()) {
                auto *selector = dynamic_cast<future::ClassSelector *>(*it);
                auto cssid = selector->getClassIdentifier();
                if (std::find(cssClasses.begin(), cssClasses.end(), cssid) != cssClasses.end()) {
                    cssInfos.emplace_back(parse_to_css_info(selector, cssid, 0));
                }
            } else if (type == future::TypeSelector::SelectorType::TypeSelector && !cssTags.empty()) {
                auto *selector = dynamic_cast<future::TypeSelector *>(*it);
                std::string identifier = selector->getTagName();
                if (std::find(cssTags.begin(), cssTags.end(), identifier) != cssTags.end()) {
                    cssInfos.emplace_back(parse_to_css_info(selector, identifier, 1));
                }

            } else if (type == future::IdSelector::SelectorType::IDSelector && !cssIds.empty()) {
                auto *selector = dynamic_cast<future::IdSelector *>(*it);
                std::string identifier = selector->getIdIdentifier();
                if (std::find(cssIds.begin(), cssIds.end(), identifier) != cssIds.end()) {
                    cssInfos.emplace_back(parse_to_css_info(selector, identifier, 2));
                }
            }
        }
    }
}
