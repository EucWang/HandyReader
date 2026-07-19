//
// Created by wxn on 2026-03-30.
//

#include "xml2_ext.h"

int xml2_ext::normalize_xml(std::string& str_xml) { if (str_xml.empty()) {
        LOGD("%s failed: empty input", __func__);
        return LIBXML_ERR_EMPTY_INPUT;
    }

    xmlResetLastError();

    int options = XML_PARSE_RECOVER | XML_PARSE_NOENT |
                  XML_PARSE_NOBLANKS | XML_PARSE_NONET;

    xmlDocPtr doc = xmlReadMemory(str_xml.c_str(),
                                  static_cast<int>(str_xml.size()),
                                  nullptr, "UTF-8", options);
    if (!doc) {
        const xmlError* err = xmlGetLastError();
        LOGD("%s parse failed: %s", __func__, err ? err->message : "unknown error");
        return LIBXML_ERR_PARSE;
    }

    xmlBufferPtr buf = xmlBufferCreate();
    if (!buf) {
        xmlFreeDoc(doc);
        return LIBXML_ERR_SAVE;
    }

    // 第三个参数：0 = 紧凑输出，不带声明（如果需要声明，去掉 XML_SAVE_NO_DECL）
    xmlSaveCtxtPtr ctx = xmlSaveToBuffer(buf, "UTF-8", XML_SAVE_NO_DECL);
    if (!ctx) {
        xmlBufferFree(buf);
        xmlFreeDoc(doc);
        LOGD("%s failed to create save context", __func__);
        return LIBXML_ERR_SAVE;
    }

    int saveResult = xmlSaveDoc(ctx, doc);
    if (saveResult == -1) {
        LOGD("%s failed to save document", __func__);
        xmlSaveClose(ctx);
        xmlBufferFree(buf);
        xmlFreeDoc(doc);
        return LIBXML_ERR_SAVE;
    }

    xmlSaveClose(ctx);

    if (buf->content && buf->use > 0) {
        // 直接使用 libxml2 的输出（无声明）
        str_xml.assign(reinterpret_cast<const char*>(buf->content), buf->use);
    } else {
        LOGD("%s save produced empty output", __func__);
        xmlBufferFree(buf);
        xmlFreeDoc(doc);
        return LIBXML_ERR_EMPTY_RESULT;
    }

    xmlBufferFree(buf);
    xmlFreeDoc(doc);

    LOGD("%s success, final size: %zu", __func__, str_xml.size());
    return LIBXML_SUCCESS;
}