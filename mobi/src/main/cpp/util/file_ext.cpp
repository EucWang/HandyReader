//
// Created by MAC on 2025/5/29.
//

#include "file_ext.h"


/***
 * 判断文件是否存在，如果存在返回1；
 * 如果不存在父级路径就创建目录, 如果创建目录失败则返回-1， 否则返回0
 * @param path 文件路径
 * @return
 */
int file_ext::checkAndCreateDir(const std::string &parentPath, const std::string &fileName) {
    std::string fullPath = parentPath + separator + fileName;
    if (fs::exists(fullPath) && fs::file_size(fullPath) > 0) {
        return true;
    }

    if (!fs::exists(parentPath)) {
        if (fs::create_directories(parentPath)) {
            return 0;
        } else {
            return -1;
        }
    }
    return 0;
}