//
// Created by MAC on 2025/6/18.
//

#ifndef U_READER2_DOC_TEXT_H
#define U_READER2_DOC_TEXT_H

#include <string>
#include <vector>
#include "tag_info.h"

typedef struct DocText_ {
    std::string text;
    std::vector<TagInfo> tagInfos;
} DocText;

#endif //U_READER2_DOC_TEXT_H
