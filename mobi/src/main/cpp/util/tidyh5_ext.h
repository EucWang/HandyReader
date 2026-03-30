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
};


#endif //U_READER2_TIDYH5_EXT_H
