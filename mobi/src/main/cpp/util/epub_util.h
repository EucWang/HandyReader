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
#include "book_util.h"

/****
 * opf 中的清单
 */
typedef struct _Manifest {
    std::string href;
    std::string id;
    std::string media_type;
} BookManifest;

typedef struct _Spine {
    std::string idref;
} BookSpine;

class epub_util: public book_util {

public:
    explicit epub_util(long bookid, std::string bookpath): book_util(bookid, bookpath) {
        zipEntities.clear();
        if (1 != epub_init()) {
            initStatus = false;
        } else {
            initStatus = true;
        }
        allChapters.clear();
        currentSrc = "";
        isEmptyCss = false;
    }

    virtual ~epub_util() {
        run_flag = false;
        book_id = 0;
        allChapters.clear();
        doc.ClearError();
        doc.Clear();
        currentSrc = "";
        isSingleSrc = false;
        isEmptyCss = false;
        epub_release();
        zipEntities.clear();
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

    int getChapters(/*out*/std::vector<NavPoint> &points) override;

    int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) override;

    int getCss(std::vector<std::string> &cssClasses, std::vector<std::string> &cssTags, std::vector<std::string> &cssIds, std::vector<CssInfo> &cssInfos) override;

    int32_t getWordCount(std::vector<ChapterCount> &wordCounts) override;

private:
    mutable std::mutex m_Mutex;
    mutable std::mutex m_Mutex2;
    mutable std::mutex m_Mutex3;
    unzFile bookzip;
    std::string currentSrc;
    bool isEmptyCss;
    std::vector<BookManifest> manifests;
    std::vector<BookSpine> spines;
    MetaInfo meta_info;
    std::vector<std::string> zipEntities;

    std::string opf_path;
    std::string ncx_path;

//    int parseCssSrcList();

    int epub_init();

    void epub_release();

    int parseOpfData(std::vector<NavPoint> &points);

    int parseSrcName(std::string &input, std::string &spineSrc, std::string &anchorId);


    /***
     * 第一章没有内容，由于合并ncx 和opf可能导致的首页没有内容，则需要填充一个默认的内容
     * @param chapter
     * @param docTexts
     */
    void mockFirstPage(NavPoint &chapter, std::vector<DocText> &docTexts);


    void handle_tags(JNIEnv *env, std::vector<DocText> &docTexts);

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

    int parse_css_list();

    std::string cover_to_zip_entity(const std::string &spine_name);

protected:
};


#endif //U_READER2_EPUB_UTIL_H
