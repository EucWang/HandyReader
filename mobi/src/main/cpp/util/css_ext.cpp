//
// Created by MAC on 2025/6/19.
//

#include "css_ext.h"

void css_ext::query_css(std::string &css_data, std::vector<std::string> &cssClasses, std::vector<CssInfo> &cssInfos) {
    future::CSSParser cssParser;
    if (!css_data.empty()) {
        cssParser.parseByString(css_data);
        const std::set<future::Selector *> &set = cssParser.getSelectors();
        for (auto it = set.begin(); it != set.end(); it++) {
            auto type = (*it)->getType();
            if (type == future::ClassSelector::SelectorType::ClassSelector) {
                auto *selector = dynamic_cast<future::ClassSelector *>(*it);
                auto cssid = selector->getClassIdentifier();
                if (std::find(cssClasses.begin(), cssClasses.end(), cssid) != cssClasses.end()) {
                    int weight = selector->weight();
                    bool isBaseSelector = selector->isBaseSelector();
                    std::string ruleData = selector->getRuleData();
                    std::vector<std::string> datas = split(ruleData, ';');
                    std::vector<RuleData> params;
                    if (!datas.empty()) {
                        for (auto &data: datas) {
                            trim(data);
                            std::vector<std::string> kv = split(data, ':');
                            if (kv.size() == 2) {
                                std::string k = trim_copy(kv[0]);
                                std::string v = trim_copy(kv[1]);
                                if (!k.empty() && !v.empty()) {
                                    params.emplace_back(RuleData{k, v});
                                }
                            }
                        }
                    }
                    cssInfos.emplace_back(CssInfo{cssid, weight, isBaseSelector, params});
                    if (cssInfos.size() >= cssClasses.size()) {
                        break;
                    }
                }
            }
        }
    }
}