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

#include "../../cssparser/CSSParser/CSSParser.hpp"
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"

class mobi_util {
public:

    /***
     * 构造函数
     * @param bookid
     * @param bookpath
     */
    mobi_util(long bookid, std::string bookpath) {
        book_id = bookid;
        book_path = bookpath;
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
        isSingleSrc = false;
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

    bool initStatus;
    long book_id;
    std::string book_path;
    mutable std::mutex m_Mutex;
    mutable std::mutex m_Mutex2;
    mutable std::mutex m_Mutex3;
    MOBIRawml *mobi_rawml;
    MOBIData *mobi_data;
    std::vector<NavPoint> allChapters;
    std::vector<std::string> cssSrc;
    bool isSingleSrc;

    tinyxml2::XMLDocument doc;
    std::string currentSrc;

    int init();

    void mobi_data_free();

    int parseCssSrcList();

    void mockFirstPage(NavPoint &chapter, std::vector<DocText> &docTexts);

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
    static int parseSrcName(std::string &src,
                            std::string &prefix,
                            std::string &spineSrc,
                            int *prefixType,
                            int *srcId,
                            std::string &anchorId,
                            std::string &suffix);

    /****
     * 解析html文档得到中间数据DocText的集合
     * @param env       jni环境变量
     * @param book_id   书籍id
     * @param mobi_rawml   mobilib解析得到的数据
     * @param element       遍历xml的根元素
     * @param docTexts      最终输出的结果
     * @param startAnchorId 遍历开始的锚点id
     * @param endAnchorId   遍历结束的锚点id
     * @param flagAdd       遍历标识
     * @param spineSrcName  资源名
     * @return
     */
    static int parseHtmlDoc(JNIEnv *env,
                            long book_id,
                            MOBIRawml *mobi_rawml,
                            tinyxml2::XMLElement *element,
                            std::vector<DocText> &docTexts,
                            std::string &startAnchorId,
                            std::string &endAnchorId,
                            int *flagAdd,
                            std::string &spineSrcName,
                            std::vector<TagInfo> parentTags);

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
    static int cacheImage(JNIEnv *env, long book_id, MOBIRawml *mobi_rawml, std::string &imgSRc, int prefixType, int srcUid, int *width, int *height);

    int parseOpfData(const char *opf_data, size_t opf_data_size, std::vector<NavPoint> &points);

    static std::string
    processParagraph(const tinyxml2::XMLElement *pElem, std::vector<TagInfo> &subTags, std::string &startAnchorId, std::string &endAnchorId, int *flagAdd,
                     std::string &spineSrcName);

    static std::string getEleParams(const tinyxml2::XMLElement *elem, std::string &spineSrcName);

    static size_t parseElement(const tinyxml2::XMLElement *elem,
                               std::string &fullText,
                               std::string &parent_uuid,
                               size_t initialOffset,
                               std::vector<TagInfo> &subTags,
                               std::string &startAnchorId,
                               std::string &endAnchorId,
                               int *flagAdd,
                               std::string &spineSrcName);

    int countHtmlDoc(tinyxml2::XMLElement *element,
                     int32_t *wordCount,
                     std::string &startAnchorId,
                     std::string &endAnchorId,
                     int *flagAdd,
                     std::string &spineSrcName
//                                std::vector<TagInfo> fatherTags
    );

    int parseDocDom(int prefixType, int srcUid);

    int countHtmlDoc2(
            tinyxml2::XMLElement *element,
            std::vector<std::string> &anchors,
            std::vector<size_t> &wordCount,
            int *chapterIndex,
            size_t *chapterWordCount,
            std::string &spineSrcName
    );

    size_t countParagraph(const tinyxml2::XMLElement *pElem,
                               std::vector<std::string> &anchors,
                               std::vector<size_t> &wordCount,
                               int *chapterIndex,
                               size_t *chapterWordCount,
                               std::string &spineSrcName);

    void countElement(const tinyxml2::XMLElement *elem,
                            std::string &fullText,
                            std::vector<std::string> &anchors,
                            std::vector<size_t> &wordCount,
                            int *chapterIndex,
                            size_t *chapterWordCount,
                            std::string &spineSrcName);
};

#endif //SIMPLEREADER2_MOBI_UTIL_H
