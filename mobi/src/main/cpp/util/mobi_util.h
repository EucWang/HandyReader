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

#include "book_util.h"
#include "chapter_count.h"
#include "css_ext.h"
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"
#include "xml_ext.h"
#include "tidyh5_ext.h"

class mobi_util : public book_util {
public:

    /***
     * 构造函数
     * @param bookid
     * @param bookpath
     */
    explicit mobi_util(long bookid, std::string bookpath): book_util(bookid, bookpath) {
        mobi_rawml = nullptr;
        mobi_data = nullptr;
        initStatus = false;
        if (init() != MOBI_SUCCESS) {
            initStatus = false;
        } else {
            initStatus = true;
        }
        allChapters.clear();
        currentSrc = "";
    }

    /***
     * 析构函数
     */
    virtual ~mobi_util() {
        book_id = 0;
        mobi_data_free();
        allChapters.clear();
        doc.ClearError();
        doc.Clear();
        currentSrc = "";
        isSingleSrc = false;
    }

    static int loadMobi(std::string fullpath,
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

    int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts)  override;

    int getCss(std::vector<std::string> &cssClasses, std::vector<std::string> &cssTags, std::vector<std::string> &cssIds, std::vector<CssInfo> &cssInfos)  override;

    int32_t getWordCount(std::vector<ChapterCount> &wordCounts) override;

private:
    mutable std::mutex m_Mutex;
    mutable std::mutex m_Mutex2;
    mutable std::mutex m_Mutex3;
    mutable std::mutex m_Mutex4;
    MOBIRawml *mobi_rawml;
    MOBIData *mobi_data;

    std::string currentSrc;

    int init();

    void mobi_data_free();

    int parseCssSrcList();

    /****
 * 从资源索引路径中解析出 prefix， srcId, anchorId, suffix
 * @param src [in]
 * @param prefix [out] 资源前缀， 取值 flow, part, resource
 * @param spineSrc [out] 对应spine的资源名
 * @param prefixType [out] 资源前缀类型， 取值对应 flow 为1, part 为2, resource 为3
 * @param srcId  [out] 资源id， 对应 各个部分的uid
 * @param anchorId  [out]  资源锚点id， 如果没有则为空
 * @param suffix  [out] 对应文件类型，取值如果是文档则是 html/htm, 如果是图片则是 png,jpg,gif,jpeg
 * @return 0 失败， 1成功
 */
    static int innerParseSrcName(std::string &src,
                            std::string &prefix,
                            std::string &spineSrc,
                            int *prefixType,
                            int *srcId,
                            std::string &anchorId,
                            std::string &suffix);

    /****
     * 图片资源如果没有写入到缓存文件中，则创建图片缓存文件， 并返回图片的宽高，
     * @param env  [in] JNIEnv *
     * @param imgSRc [in] 资源名
     * @param prefixType [in] 资源的前缀类型，
     * @param srcUid [in]   资源uid
     * @param width [out]
     * @param height [out]
     * @return 成功返回1， 失败返回0
     */
    int cache_image(JNIEnv *env, std::string &imgSRc, int prefixType, int srcUid, int *width, int *height);

    int parseOpfData(const char *opf_data, size_t opf_data_size, std::vector<NavPoint> &points);

    int parseDocDom(int prefixType, int srcUid);

    void handle_tags(JNIEnv *env, std::vector<DocText> &docTexts);

    int load_entity_data(const std::string &src_name, std::string &output_data);

};

#endif //SIMPLEREADER2_MOBI_UTIL_H
