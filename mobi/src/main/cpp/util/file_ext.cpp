//
// Created by MAC on 2025/5/29.
//

#include "file_ext.h"

/***
 * 替换 文件名的 '/' 为 '_'
 * 移除文件名中开始的'..'
 * @param filename
 */
std::string file_ext::handle_filename(const std::string &filename) {
    std::string separator = "/";
    std::string underscore = "_";
    std::string predir = "..";
    std::string name;
    name.append(filename);
    if (string_ext::startWith(name, predir)) {
        name = name.substr(predir.length());
    }
    if (name.find(separator) != std::string::npos) {
        string_ext::replace_all(name, separator, underscore);
    }
    return name;
}

/***
 * 判断文件是否存在，如果存在返回1；
 * 如果不存在父级路径就创建目录, 如果创建目录失败则返回-1， 否则返回0
 * @param path 文件路径
 * @return
 */
int file_ext::checkAndCreateDir(const std::string &parentPath, const std::string &fileName) {
    std::string fullPath = parentPath + separator + handle_filename(fileName);
    if (fs::exists(fullPath) && fs::file_size(fullPath) > 0) {
        return 1;
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

int file_ext::checkPath(const std::string &path) {
    if (fs::exists(path) && fs::file_size(path) > 0) {
        return 1;
    }
    return 0;
}

int file_ext::writeDataToFile(const std::string &filepath, unsigned char *data, size_t data_size) {
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

std::string
file_ext::get_cover_path(std::string &book_title, std::string &file_ext) {
    std::string file_name = book_title + "_cover." + file_ext;
    std::string parent_path = app_ext::appFileDir + "/" + "covers";
    if (!dir_exists(parent_path.c_str()) && make_directory(parent_path.c_str()) != SUCCESS) {
        return "";
    }
    return parent_path + "/" + file_name;
}

std::string file_ext::get_media_type_ext(std::string &media_type) {
    std::string ext;
    if (media_type == "image/jpeg" || media_type == "image/jpg") {
        ext = "jpg";
    } else if (media_type == "image/png") {
        ext = "png";
    } else if (media_type == "image/gif") {
        ext = "gif";
    } else if (media_type == "image/bmp") {
        ext = "bmp";
    } else if (media_type == "image/webp") {
        ext = "webp";
    }
    return ext;
}

std::string file_ext::get_img_path(long book_id, const std::string &imgSrc) {
    return get_img_parent_path(book_id) + separator + handle_filename(imgSrc);
}

std::string file_ext::get_img_parent_path(long book_id) {
    return app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id);
}

std::string file_ext::get_file_suffix(std::string &path_name) {
    auto index = path_name.find_last_of('.');
    if (index != std::string::npos) {
        return path_name.substr(index + 1);
    }
    return "";
}