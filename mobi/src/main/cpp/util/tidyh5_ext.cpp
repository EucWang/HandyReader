//
// Created by MAC on 2025/6/19.
//

#include "tidyh5_ext.h"

/***
 * @param format_str [in/out] 需要格式化的字符串，
 * @return 1 成功，0 失败
 */
int tidyh5_ext::tidy_html(std::string &format_str) {
    if (format_str.empty()) {
        return 1;
    }

    std::string output_str;
    unsigned char *normalizedHtml = nullptr;
    size_t normalizedHtmlSize = 0;
    TidyDoc tdoc = tidyCreate();
    TidyBuffer output = {0};
    TidyBuffer errbuf = {0};

    //tidy options
    tidyOptSetBool(tdoc, TidyXmlOut, yes); //output xhtml
    tidyOptSetBool(tdoc, TidyQuiet, yes);   //抑制警告
    tidyOptSetInt(tdoc, TidyWrapLen, 0);                //禁用换行
    tidyOptSetValue(tdoc, TidyCharEncoding, "utf8");    //编码集

    tidyParseString(tdoc, format_str.c_str());
    if (tidyCleanAndRepair(tdoc) >= 0 && tidySaveBuffer(tdoc, &output) >= 0) {
        normalizedHtml = output.bp;
        normalizedHtmlSize = output.size;
    } else {
        unsigned char *errInfo = errbuf.bp;
        LOGE("%s:failed %s", __func__, errInfo);
        return 0;
    }

    if (normalizedHtml == nullptr || normalizedHtmlSize <= 0) {
        LOGE("%s:failed, tidy html failed", __func__);
    } else {
        format_str = std::string(normalizedHtml, normalizedHtml + normalizedHtmlSize);
    }
    return 1;
}
