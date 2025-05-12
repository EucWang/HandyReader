//
// Created by MAC on 2025/4/17.
//

#ifndef SIMPLEREADER2_MOBI_UTIL_H
#define SIMPLEREADER2_MOBI_UTIL_H

#include <string>
#include "../util/log.h"

extern "C" {
#include "mobi/common.h"
#include "mobi/mobitool.h"
}

#include "mobi/save_epub.h"
#include <iostream>
#include <filesystem> // C++17 标准库

namespace fs = std::filesystem;

class mobi_util {

public:
    static int loadMobi(std::string fullpath,
                        std::string appFileDir,
                        std::string& coverPath,
//                        std::string& epubPath,

                        std::string& title,
                        std::string& author,
                        std::string& contributor,

                        std::string& subject,
                        std::string& publisher,
                        std::string& date,

                        std::string& description,
                        std::string& review,
                        std::string& imprint,

                        std::string& copyright,
                        std::string& isbn,
                        std::string& asin,

                        std::string& language,
                        std::string& identifier,
                        bool& isEncrypted);

private:

};


#endif //SIMPLEREADER2_MOBI_UTIL_H
