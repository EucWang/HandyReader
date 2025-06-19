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
#include <string>

class tidyh5_ext {
public:
    /***
     * @param format_str [in/out] 需要格式化的html字符串，
     * @return 1 成功，0 失败
     */
    static int tidy_html(std::string &format_str);

};


#endif //U_READER2_TIDYH5_EXT_H
