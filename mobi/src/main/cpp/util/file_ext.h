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
#include "app_ext.h"
#include "string_ext.h"

#include <iostream>

namespace fs = std::filesystem;

class file_ext {

public:

    /***
     * 替换 文件名的 '/' 为 '_'
     * 移除文件名中开始的'..'
     * @param filename
     */
    static std::string handle_filename(const std::string &filename);

    static int checkAndCreateDir(const std::string &parentPath, const std::string &fileName);

    static int writeDataToFile(const std::string &filepath, unsigned char* data, size_t data_size);

    /***
     * 根据书名，图片类型后缀，得到给定目录下的封面图片路径
     * @param book_title
     * @param file_ext   取值：jpg gif png bmp
     * @param output_path [out] 输出路径
     * @return 1 成功， 0 失败，创建目录失败
     */
    static std::string get_cover_path(std::string &book_title, std::string &file_ext);

    /***
     * 检查文件路径是否存在，以及文件是否有内容， 有则返回1， 无则返回0
     * @param path  文件完整路径
     * @return
     */
    static int checkPath(const std::string &path);

    /***
     * 根据media-type 得到图片文件后缀
     * @param media_type
     * @return
     */
    static std::string get_media_type_ext(std::string &media_type);

    static std::string get_img_path(long book_id, const std::string &imgSrc);

    static std::string get_img_parent_path(long book_id);

    /****
     * 获取文件名的后缀字符串
     * @param path_name
     * @return
     */
    static std::string get_file_suffix(std::string &path_name);
};


#endif //U_READER2_FILE_EXT_H
