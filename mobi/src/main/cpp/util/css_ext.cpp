//
// Created by MAC on 2025/6/19.
//

#include "css_ext.h"

CssInfo parse_to_css_info(future::Selector *selector, std::string &identifier, int type) {
    std::string ruleData = selector->getRuleData();
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
    cssParser.parseByString(css_data);
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