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
};


#endif //U_READER2_CSS_EXT_H
