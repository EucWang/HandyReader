//
// Created by MAC on 2025/5/29.
//

#ifndef U_READER2_FILE_EXT_H
#define U_READER2_FILE_EXT_H

extern "C" {
#include "mobi/common.h"
#include <fcntl.h>   // 包含 open() 等文件操作函数
#include <unistd.h>  // 包含 close() 等文件操作函数
}
#include "log.h"
#include <string>
#include <filesystem> // C++17 标准库

#include <iostream>

namespace fs = std::filesystem;

class file_ext {

public:
    static int checkAndCreateDir(const std::string &parentPath, const std::string &fileName);

    static int writeDataToFile(const std::string &filepath, unsigned char* data, size_t data_size);
};


#endif //U_READER2_FILE_EXT_H
