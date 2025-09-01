//
// Created by MAC on 2025/6/25.
//

#ifndef U_READER2_BOOK_UTIL_H
#define U_READER2_BOOK_UTIL_H

#include <string>
#include <vector>
#include <list>
#include <set>
#include <iomanip>
#include <sstream>
#include <random>
#include <vector>

#include "../util/log.h"
#include "bitmap_ext.h"
#include "app_ext.h"
#include "string_ext.h"
#include "file_ext.h"

#include "chapter_count.h"
#include "css_ext.h"
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"
#include "xml_ext.h"
#include "meta_data.h"

class book_util {
public:
    explicit book_util(long bookid, std::string bookpath) : run_flag(true), book_id(bookid), book_path(bookpath), isSingleSrc(false) {

    }

    virtual ~book_util() {
        run_flag = false;
    }

    virtual int getChapters(/*out*/std::vector<NavPoint> &points) = 0;

    virtual int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) = 0;

    virtual int getCss(std::vector<std::string> &cssClasses,
                       std::vector<std::string> &cssTags,
                       std::vector<std::string> &cssIds,
                       std::vector<CssInfo> &cssInfos) = 0;

    virtual int32_t getWordCount(std::vector<ChapterCount> &wordCounts) = 0;

    long bookid() {
        return book_id;
    }

    std::string &bookpath() {
        return book_path;
    }

private:
protected:
    tinyxml2::XMLDocument doc;
    std::vector<std::string> cssSrc;
    std::vector<NavPoint> allChapters;
    bool initStatus;
    long book_id;
    std::string book_path;
    MetaInfo meta_info;

    bool isSingleSrc;
    volatile bool run_flag;

    int parseSrcName(std::string &inputSrc/*in*/,
                     std::string &spineSrc/*out*/,
                     std::string &anchorId/*out*/) {
//    LOGI("%s:invoke", __func__);
        if (inputSrc.find('#') != std::string::npos) {
            std::vector<std::string> parts = string_ext::split(inputSrc, '#');
            if (parts.size() == 2) {
                spineSrc = parts[0];
                anchorId = parts[1];
            }
        } else {
            spineSrc = inputSrc;
        }

//    LOGI("%s:invoke done", __func__);
        return 1;
    }


    /***
     * 第一章没有内容，由于合并ncx 和opf可能导致的首页没有内容，则需要填充一个默认的内容
     * @param chapter
     * @param docTexts
     * @param title
     * @param author
     * @param publisher
     */
    void mockFirstPage(NavPoint &chapter, std::vector<DocText> &docTexts, const std::string &title, const std::string &author, const std::string &publisher) {
        LOGD("%s chapter[%s], title[%s], author[%s],publisher[%s]", __func__, chapter.text.c_str(), title.c_str(), author.c_str(), publisher.c_str());
        if (docTexts.empty() && chapter.playOrder == 1) {
            LOGI("%s:invoke", __func__);
//            std::string &title = meta_info.title;
//            std::string &author = meta_info.author;
//            std::string &publisher = meta_info.publisher;
            if (!title.empty()) {
                std::vector<TagInfo> tagInfos;
                tagInfos.push_back(TagInfo{
                        string_ext::generate_uuid(),
                        "",
                        "h1",
                        0,
                        title.length(),
                        "",
                        ""
                });
                docTexts.emplace_back(DocText{title, tagInfos});
            }
            if (!author.empty()) {
                std::vector<TagInfo> tagInfos;
                tagInfos.push_back(TagInfo{
                        string_ext::generate_uuid(),
                        "",
                        "p",
                        0,
                        author.length(),
                        "",
                        "align=center"
                });
                docTexts.emplace_back(DocText{author, tagInfos});
            }
            if (!publisher.empty()) {
                std::vector<TagInfo> tagInfos;
                tagInfos.push_back(TagInfo{
                        string_ext::generate_uuid(),
                        "",
                        "p",
                        0,
                        publisher.length(),
                        "",
                        "align=center"
                });
                docTexts.emplace_back(DocText{publisher, tagInfos});
            }
            LOGI("%s:invoke done", __func__);
        }
    }
};

#endif //U_READER2_BOOK_UTIL_H
