//
// Created by MAC on 2025/5/29.
//

#ifndef U_READER2_FILE_EXT_H
#define U_READER2_FILE_EXT_H

extern "C" {
#include "mobi/common.h"
}
#include <string>
#include <filesystem> // C++17 标准库

namespace fs = std::filesystem;

class file_ext {

public:
    static int checkAndCreateDir(const std::string &parentPath, const std::string &fileName);
};


#endif //U_READER2_FILE_EXT_H
