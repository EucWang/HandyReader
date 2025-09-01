//
// Created by wxn on 2025/7/14.
//

#ifndef HANDYREADER_FB2_UTIL_H
#define HANDYREADER_FB2_UTIL_H

#include "book_util.h"
#include <string>


#include <string>
#include "../util/log.h"

extern "C" {
#include "tidy.h"
#include "tidybuffio.h"
#include "../unzip101e/unzip.h"
}

#include "../base64/decode.h"
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
#include <istream>
#include <ostream>
#include <sstream>
#include <fstream>
#include <filesystem> // C++17 标准库

#include <thread>
#include <stdexcept>
#include <mutex>

#include "chapter_count.h"
#include "zip_ext.h"
#include "xml_ext.h"
#include "utf8.h"
#include <android/bitmap.h>
#include <android/imagedecoder.h>
#include <list>
#include <stack>
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"
#include "tidyh5_ext.h"
#include "meta_data.h"
#include "css_ext.h"


class fb2_util : public book_util {

public:

    explicit fb2_util(long bookid, const std::string &path) : book_util(bookid, path) {
        if (1 != fb2_init()) {
            initStatus = false;
        } else {
            initStatus = true;
        }
        allChapters.clear();
        currentSrc = "";
        isEmptyCss = false;
    }

    virtual  ~fb2_util() {
        book_id = 0;
        allChapters.clear();
        doc.ClearError();
        doc.Clear();
        currentSrc = "";
        isSingleSrc = false;
        isEmptyCss = false;
        fb2_release();
    }

    static int load_fb2(const char *fullpath,
                        std::string &coverPath,
                        std::string &title,

                        std::string &author,
                        std::string &contributor,
                        std::string &subjecct,

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
                        bool &isEncripted
    );

    int getChapters(/*out*/std::vector<NavPoint> &points) override;

    int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter,
                   std::vector<DocText> &docTexts) override;

    int getCss(std::vector<std::string> &cssClasses, std::vector<std::string> &cssTags,
               std::vector<std::string> &cssIds, std::vector<CssInfo> &cssInfos) override;

    int32_t getWordCount(std::vector<ChapterCount> &wordCounts) override;

private:
    mutable std::mutex m_Mutex;
    mutable std::mutex m_Mutex2;
    mutable std::mutex m_Mutex3;
    mutable std::mutex m_Mutex4;

    std::string currentSrc;

    bool isEmptyCss;

    int fb2_init();

    void fb2_release();

    /***
     * 缓存图片
     * @param env
     * @param imgSrc
     * @param width
     * @param height
     * @return
     */
    int cache_image(JNIEnv *env,
                    std::string &imgSrc,
                    int *width,
                    int *height);

    void handle_tags(JNIEnv *env, std::vector<DocText> &docTexts);


};


#endif //HANDYREADER_FB2_UTIL_H
