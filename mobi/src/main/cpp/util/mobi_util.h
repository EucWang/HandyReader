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

#include "tidy.h"
#include "tidybuffio.h"
}

#include "string_ext.h"
#include <iomanip>
#include <sstream>
#include <random>
#include <vector>
#include "tinyxml2.h"
#include "mobi/save_epub.h"
#include <iostream>
#include <filesystem> // C++17 标准库

#include "utf8.h"

namespace fs = std::filesystem;

typedef struct NavPoint_{
    std::string id;
    std::string parentId;
    int playOrder;
    std::string text;
    std::string src;

    bool operator<(const struct NavPoint_& other) const { //排序用
        return playOrder < other.playOrder;
    }
} NavPoint;

/***
 * 对DocText的修饰，一些常见的css样式的简化
 */
typedef struct TagInfo_ {
    std::string uuid;       //唯一标识
    std::string anchor_id;         //作为锚点使用的id
    std::string name;       //名称，用于区分不同的修饰符类型
    size_t startPos;              //开始位置
    size_t endPos;                //结束位置

    std::string parent_uuid;    //父级uuid
    std::string params;           //字符串拼接的键值对，
} TagInfo;

typedef struct DocText_ {
    std::string text;
    std::vector<TagInfo> tagInfos;
} DocText;

class mobi_util {
public:

    static int loadMobi(std::string fullpath,
                        std::string appFileDir,
                        std::string& coverPath,

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

//    static int convertToEpub(
//            std::string fullpath,
//            std::string appCacheeDir,
//            std::string& epubPath);

    static int getChapters(long book_id, const char* path,  /*out*/std::vector<NavPoint>& points);

    static int getChapter(long book_id, const char *path, const char *app_file_dir, NavPoint& chapter, std::vector<DocText> &docTexts);

    static void free_data();
private:
    //缓存上次创建的书籍信息
    static long last_book_id;
    static std::string last_path;
    static MOBIRawml *mobi_rawml;
    static  MOBIData *mobi_data;

    static int init(long book_id, const char* path);
};

#endif //SIMPLEREADER2_MOBI_UTIL_H
