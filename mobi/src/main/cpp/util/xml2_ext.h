//
// Created by wxn on 2026-03-30.
//

#ifndef HANDYREADER_XML2_EXT_H
#define HANDYREADER_XML2_EXT_H

#include <string>
#include <libxml/parser.h>
#include <libxml/tree.h>
#include <libxml/xmlsave.h>
#include <libxml/xmlerror.h>
#include "log.h"

// 错误码定义（建议与你原有 tidy 错误码保持一致）
const int LIBXML_SUCCESS           = 0;
const int LIBXML_ERR_EMPTY_INPUT   = 1;
const int LIBXML_ERR_PARSE         = 2;
const int LIBXML_ERR_EMPTY_RESULT  = 3;
const int LIBXML_ERR_SAVE          = 4;

class xml2_ext {
public:

    static int normalize_xml(std::string &xml_str);
};


#endif //HANDYREADER_XML2_EXT_H
