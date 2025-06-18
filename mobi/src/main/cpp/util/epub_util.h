//
// Created by MAC on 2025/6/18.
//

#ifndef U_READER2_EPUB_UTIL_H
#define U_READER2_EPUB_UTIL_H

#include <string>
#include "../util/log.h"

extern "C" {
#include "tidy.h"
#include "tidybuffio.h"
#include "../unzip101e/unzip.h"
}

#include "bitmap_ext.h"
#include "app_ext.h"
#include "string_ext.h"
#include "file_ext.h"
#include <iomanip>
#include <sstream>
#include <random>
#include <vector>
#include "tinyxml2.h"
#include "mobi/save_epub.h"
#include <iostream>
#include <filesystem> // C++17 标准库

#include <thread>
#include <stdexcept>
#include <mutex>

#include "utf8.h"
#include <android/bitmap.h>
#include <android/imagedecoder.h>
#include <list>
#include <stack>

#include "../../cssparser/CSSParser/CSSParser.hpp"
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"

class epub_util {

public:
    explicit epub_util(long bookid, std::string bookpath) {
        book_id = bookid;
        book_path = bookpath;
    }

    virtual ~epub_util() {
        book_id = 0;
        allChapters.clear();
        doc.ClearError();
        doc.Clear();
        currentSrc = "";
        isSingleSrc = false;
    }

    /***
     *
     * @param fullpath  文件路径
     * @param coverPath     封面输出路径
     * @param title       书名
     * @param author    作者
     * @param contributor  提供者
     * @param subject       分类
     * @param publisher     发布者
     * @param date      发布日期
     * @param description   描述
     * @param review        预览
     * @param imprint       版本说明
     * @param copyright     版权
     * @param isbn      isbn
     * @param asin  asin
     * @param language 语言
     * @param identifier    唯一标识
     * @param isEncrypted 是否加密
     * @return 1 成功， 0失败
     */
    static int load_epub(std::string fullpath,
                         std::string &coverPath,

                         std::string &title,
                         std::string &author,
                         std::string &contributor,

                         std::string &subject,
                         std::string &publisher,
                         std::string &date,

                         std::string &description,
                         std::string &review,
                         std::string &imprint,

                         std::string &copyright,
                         std::string &isbn,
                         std::string &asin,

                         std::string &language,
                         std::string &identifier,
                         bool &isEncrypted);

    int getChapters(/*out*/std::vector<NavPoint> &points);

    int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts);

    int getCss(std::vector<std::string> &cssClasses, std::vector<CssInfo> &cssInfos);

    int32_t getWordCount(std::vector<std::pair<int32_t, int32_t>> &wordCounts);

    long bookid() {
        return book_id;
    }

    std::string &bookpath() {
        return book_path;
    }

private:
    long book_id;
    std::string book_path;
    bool initStatus = false;
    mutable std::mutex m_Mutex;
    std::vector<NavPoint> allChapters;
    std::vector<std::string> cssSrc;
    tinyxml2::XMLDocument doc;
    std::string currentSrc;
    bool isSingleSrc;

//    int parseCssSrcList();

//    void mockFirstPage(NavPoint &chapter, std::vector<DocText> &docTexts);

    int epub_init();

protected:
};


#endif //U_READER2_EPUB_UTIL_H
