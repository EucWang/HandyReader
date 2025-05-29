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

int file_ext::writeDataToFile(const std::string &filepath, unsigned char* data, size_t data_size) {
    int fd = open(filepath.c_str(), O_CREAT | O_TRUNC | O_RDWR, 0666);
    if (fd == -1) {
        LOGE("%s:failed,can't create or open img path[%s]", __func__, filepath.c_str());
        return 0;
    }
    int ret = write(fd, data, data_size);
    if (ret == -1) {
        LOGE("%s:failed,can't write data to path[%s]", __func__, filepath.c_str());
        return 0;
    } else {
        LOGE("%s:write data to path[%s] success", __func__, filepath.c_str());
    }
    close(fd);
    return 1;
}