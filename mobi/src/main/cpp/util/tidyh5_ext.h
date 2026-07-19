//
// Created by MAC on 2025/6/19.
//

#ifndef U_READER2_TIDYH5_EXT_H
#define U_READER2_TIDYH5_EXT_H

#include <string>
#include "../util/log.h"

extern "C" {
#include "tidy.h"
#include "tidybuffio.h"
#include "../unzip101e/unzip.h"
}
#include <memory>
#include <optional>
#include "string_ext.h"

// 错误码定义
const int TIDY_SUCCESS           = 0;
const int TIDY_ERR_EMPTY_INPUT   = 1;
const int TIDY_ERR_CREATE_DOC    = 2;
const int TIDY_ERR_SET_OPTIONS   = 3;
const int TIDY_ERR_PARSE         = 4;
const int TIDY_ERR_CLEAN_REPAIR  = 5;
const int TIDY_ERR_SAVE          = 6;
const int TIDY_ERR_EMPTY_RESULT  = 7;

class tidyh5_ext {
public:
    /***
     * @param format_str [in/out] 需要格式化的html字符串，
     * @return 1 成功，0 失败
     */
    static int tidy_html(std::string &format_str);

    static int tidy_html_with_css(std::string &format_str, std::string &page_css_style);

    /***
     * body-only tidy：输出 <body> 内层内容，不包裹 <html>/<head>/<body>。
     * 用于 renderSplitSegment 对 raw 段片段的整理（tidy 下沉）：
     * 普通 tidy_html 对片段会输出完整文档，二次包裹导致嵌套 <html>。
     * @param format_str [in/out] raw HTML 片段
     * @return 1 成功，0 失败
     */
    static int tidy_html_body_only(std::string &format_str);
};


#endif //U_READER2_TIDYH5_EXT_H
