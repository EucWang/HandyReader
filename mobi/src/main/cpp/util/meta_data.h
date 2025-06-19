//
// Created by MAC on 2025/6/19.
//

#ifndef U_READER2_META_DATA_H
#define U_READER2_META_DATA_H

#include <string>

typedef struct _MetaInfo {

    std::string title;
    std::string author;
    std::string contributor;

    std::string subject;
    std::string publisher;
    std::string date;

    std::string description;
    std::string review;
    std::string imprint;

    std::string copyright;
    std::string isbn;
    std::string asin;

    std::string language;
    bool isEncrypted;

    std::string coverPath;
} MetaInfo;

#endif //U_READER2_META_DATA_H
