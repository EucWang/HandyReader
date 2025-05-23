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

#include "tinyxml2.h"
#include "mobi/save_epub.h"
#include <iostream>
#include <filesystem> // C++17 标准库

namespace fs = std::filesystem;

typedef struct NavPoint_{
    std::string id;
    int playOrder;
    std::string text;
    std::string src;

    bool operator<(const struct NavPoint_& other) const { //排序用
        return playOrder < other.playOrder;
    }
} NavPoint;

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

    static std::vector<NavPoint> getChapters(long book_id, const char* path);

    static void getChapter(long book_id, const char *path, const char *app_file_dir, int chapter_index);

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
