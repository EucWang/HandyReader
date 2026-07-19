//
// Created by wxn on 2026-05-27.
//

#ifndef HANDYREADER_HTML_UTIL_H
#define HANDYREADER_HTML_UTIL_H


#include "book_util.h"
#include <string>
#include "../util/log.h"

extern "C" {
#include "tidy.h"
#include "tidybuffio.h"
}

#include "bitmap_ext.h"
#include "app_ext.h"
#include "string_ext.h"
#include "file_ext.h"
#include <vector>
#include "tinyxml2.h"
#include <mutex>

#include "chapter_count.h"
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"
#include "tidyh5_ext.h"
#include "meta_data.h"
#include "css_ext.h"
#include "xml_ext.h"


class html_util : public book_util {
public:

    explicit html_util(long bookid, const std::string &path) : book_util(bookid, path) {
        if (1 != html_init()) {
            initStatus = false;
        } else {
            initStatus = true;
        }
        allChapters.clear();
        currentSrc = "";
    }

    virtual  ~html_util() {
        book_id = 0;
        allChapters.clear();
        chapterDoc.ClearError();
        chapterDoc.Clear();
        currentSrc = "";
        isSingleSrc = true;
        html_release();
    }

    int getChapters(/*out*/std::vector<NavPoint> &points) override;

    int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter,
                   std::vector<DocText> &docTexts) override;

    int32_t getWordCount(std::vector<ChapterCount> &wordCounts) override;


private:
    mutable std::mutex m_Mutex;
    mutable std::mutex m_Mutex2;
    mutable std::mutex m_Mutex3;

    std::string currentSrc;

    tinyxml2::XMLDocument chapterDoc;

    int html_init();

    void html_release();

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

    void handle_tags(JNIEnv *env, std::vector<DocText> &docTexts, std::vector<CssInfo> &cssInfos);
};


#endif //HANDYREADER_HTML_UTIL_H
