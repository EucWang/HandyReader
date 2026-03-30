//
// Created by MAC on 2025/6/19.
//

#include "tidyh5_ext.h"


std::string extract_style_with_tidy(TidyDoc &tdoc) {
    LOGD("%s start", __func__);
    TidyNode node = nullptr;
    std::string css;
    TidyNode head = tidyGetHead(tdoc);
    if (head) {
        for (node = tidyGetChild(head); node; node = tidyGetNext(node)) {
            const char* name = tidyNodeGetName(node);
            if (name && strcmp(name, "style") == 0) {
                TidyBuffer buf;
                tidyBufInit(&buf);
                tidyNodeGetText(tdoc, node, &buf);
                css.assign((char*)buf.bp, buf.size);
                tidyBufFree(&buf);
                break;
            }
        }
    }
    LOGD("%s return css: %s", __func__, css.c_str());
    return css;
}

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

    tidyRelease(tdoc);
    return 1;
}

int tidyh5_ext::tidy_html_with_css(std::string &format_str, std::string &page_css_style) {
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
    if (tidyCleanAndRepair(tdoc) >= 0) {
        page_css_style = extract_style_with_tidy(tdoc);

        if (tidySaveBuffer(tdoc, &output) >= 0) {
            normalizedHtml = output.bp;
            normalizedHtmlSize = output.size;
        }
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

    tidyRelease(tdoc);
    return 1;
}
