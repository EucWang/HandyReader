//
// Created by MAC on 2025/5/29.
//

#include "file_ext.h"

/***
 * 替换 文件名的 '/' 为 '_'
 * 移除文件名中开始的'..'
 * 规范化文件名，防止出现问题
 * @param filename
 */
std::string file_ext::handle_filename(const std::string &filename) {
    if (filename.empty()) {
        return "unknown";
    }
    std::string name = filename;

    // 去除前缀
    static const std::string prefixes[] = {"../", "./", "..\\"};
    for (auto &prefix : prefixes) {
        if (string_ext::startWith(name, prefix)) {
            name = name.substr(prefix.length());
            break;
        }
    }

    // 替换路径分隔符和特殊字符
    for (char &c : name) {
        if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' ||
            c == '"' || c == '<' || c == '>' || c == '|' || c == '\0') {
            c = '_';
        } else if ((unsigned char)c < 0x20) {  // 控制字符
            c = '_';
        }
    }

    // 截断超长文件名（保留后缀）
    const size_t MAX_NAME_LEN = 200;
    if (name.length() > MAX_NAME_LEN) {
        auto dotPos = name.find_last_of('.');
        if (dotPos != std::string::npos && dotPos > MAX_NAME_LEN - 10) {
            std::string ext = name.substr(dotPos);
            if (ext.length() < MAX_NAME_LEN) {
                name = name.substr(0, MAX_NAME_LEN - ext.length()) + ext;
            } else {
                name = name.substr(0, MAX_NAME_LEN);
            }
        } else {
            name = name.substr(0, MAX_NAME_LEN);
        }
    }

    // 修复上一步截断可能导致的多字节 UTF-8 字符不完整问题
    while (!name.empty()) {
        unsigned char c = static_cast<unsigned char>(name.back());
        if (c < 0x80) break;

        if ((c & 0xC0) == 0x80) {
            size_t pos = name.length();
            int continuationCount = 0;
            while (pos > 0 && (static_cast<unsigned char>(name[pos - 1]) & 0xC0) == 0x80) {
                pos--;
                continuationCount++;
            }
            if (pos > 0) {
                unsigned char lead = static_cast<unsigned char>(name[pos - 1]);
                int expectedLen = 0;
                if ((lead & 0xE0) == 0xC0) expectedLen = 2;
                else if ((lead & 0xF0) == 0xE0) expectedLen = 3;
                else if ((lead & 0xF8) == 0xF0) expectedLen = 4;
                if (expectedLen > 0 && (continuationCount + 1) >= expectedLen) {
                    break;
                }
            }
            name = name.substr(0, pos > 0 ? pos - 1 : 0);
        } else {
            name.pop_back();
        }
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
    std::error_code ec;
    std::string fullPath = parentPath + separator + handle_filename(fileName);
    if (fs::exists(fullPath, ec) && !ec) { //文件已经存在并且有内容，则直接返回
        auto size = fs::file_size(fullPath, ec);
        if (!ec && size > 0) {
            return 1;
        }
    }

    if (!fs::exists(parentPath, ec) || ec) {
        if (fs::create_directories(parentPath, ec)) {
            return 0;
        } else {
            LOGE("%s: failed to create dir[%s], error[%s]",
                 __func__, parentPath.c_str(), ec.message().c_str());
            return -1;
        }
    }
    return 0;
}

int file_ext::checkPath(const std::string &path) {
    std::error_code ec;
    if (fs::exists(path, ec) && !ec) {
        auto size = fs::file_size(path, ec);
        if (!ec && size > 0) {
            return 1;
        }
    }
    return 0;
}

int file_ext::writeDataToFile(const std::string &filepath, unsigned char *data, size_t data_size) {
    int fd = open(filepath.c_str(), O_CREAT | O_TRUNC | O_RDWR, 0666);
    if (fd == -1) {
        LOGW("%s:failed,can't create or open img path[%s]", __func__, filepath.c_str());
        return 0;
    }
    int ret = write(fd, data, data_size);
    if (ret == -1) {
        LOGW("%s:failed,can't write data to path[%s]", __func__, filepath.c_str());
        return 0;
    } else {
        LOGD("%s:write data to path[%s] success", __func__, filepath.c_str());
    }
    close(fd);
    return 1;
}

std::string
file_ext::get_cover_path(std::string &book_title, std::string &file_ext) {
    std::string file_name = handle_filename(book_title) + "_cover." + file_ext;
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

/***
 * thepath 需要计算和判断的路径,有可能是绝对路径,有可能是相对路径, 还有可能只是一个文件名
 * 如果是绝对路径,则直接返回,
 * 如果是相对路径或者只是一个文件名,则可以依据absolate_path这个路径来确定其真实的绝对路径,
 *
 * @param thepath  需要计算和判断的路径,
 * @param absolate_path  一个文件的绝对路径
 * @return
 */
std::string file_ext::calc_file_path_by_other_obsolate_path(const std::string &thepath, const std::string &absolate_path) {
    if (thepath.empty()) {
        return "";
    }

    if (string_ext::startWith(thepath, "/")) {
        return thepath;
    }

    try {
        fs::path parentDir;
        if (!absolate_path.empty()) {
            parentDir = fs::path(absolate_path).parent_path();
        }

        if (parentDir.empty()) {
            parentDir = ".";
        }

        fs::path result = parentDir / thepath;
        return fs::weakly_canonical(result).string();
    } catch (const std::exception &e) {
        LOGE("%s: failed to calc path, thepath[%s], absolate_path[%s], error[%s]",
             __func__, thepath.c_str(), absolate_path.c_str(), e.what());
        return "";
    }
}

// 提取文件名（支持 / 和 \）
std::string file_ext::extractFilename(const std::string& path) {
    if(path.empty()) {
        return "";
    }
    size_t lastSep = path.find_last_of("/\\");
    return (lastSep == std::string::npos) ? path : path.substr(lastSep + 1);
}


int file_ext::readStringFromFile(const std::string &filepath, std::string &output) {
    std::error_code ec;
    uintmax_t fileSize = fs::file_size(filepath, ec);
    if (ec) {
        LOGE("%s:failed to get file size[%s]", __func__, filepath.c_str());
        return -1;
    }
    int fd = open(filepath.c_str(), O_RDONLY);
    if (fd == -1) {
        LOGE("%s:failed,can't open file[%s]", __func__, filepath.c_str());
        return -1;
    }
    output.resize(fileSize);
    char* buf = output.data();
    size_t totalRead = 0;
    while (totalRead < fileSize) {
        ssize_t ret = read(fd, buf + totalRead, fileSize - totalRead);
        if (ret == -1) {
            LOGE("%s:failed to read file[%s]", __func__, filepath.c_str());
            close(fd);
            output.clear();
            return -1;
        }
        if (ret == 0) break;
        totalRead += ret;
    }
    close(fd);
    if (totalRead < fileSize) {
        output.resize(totalRead);
    }
    return static_cast<int>(totalRead);
}
