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
#include <android/bitmap.h>
#include <android/imagedecoder.h>
#include <sys/system_properties.h>


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

    static int getChapters(JNIEnv *env, long book_id, const char* path,  /*out*/std::vector<NavPoint>& points);

    static int getChapter(JNIEnv *env, long book_id, const char *path, const char *app_file_dir, NavPoint& chapter, std::vector<DocText> &docTexts);

    static void free_data();
private:
    //缓存上次创建的书籍信息
    static long last_book_id;
    static std::string last_path;
    static MOBIRawml *mobi_rawml;
    static  MOBIData *mobi_data;
    static std::string appFileDir;
    static JNIEnv *jniEnv;

    static int init(JNIEnv *env, long book_id, const char* path);

    /****
 * 从资源索引路径中解析出 prefix， srcId, anchorId, suffix
 * @param src [in]
 * @param prefix [out] 资源前缀， 取值 flow, part, resource
 * @param prefixType [out] 资源前缀类型， 取值对应 flow 为1, part 为2, resource 为3
 * @param srcId  [out] 资源id， 对应 各个部分的uid
 * @param anchorId  [out]  资源锚点id， 如果没有则为空
 * @param suffix  [out] 对应文件类型，取值如果是文档则是 html/htm, 如果是图片则是 png,jpg,gif,jpeg
 * @return 0 失败， 1成功
 */
    static int parseSrcName(std::string& src,
                     std::string& prefix,
                     int* prefixType,
                     int* srcId,
                     std::string& anchorId,
                     std::string& suffix);

    static int parseHtmlDoc(tinyxml2::XMLElement *element, std::vector<DocText>& docTexts);

    static int getImageOption(const char* path, int* width, int* height);

    static int cacheImage(std::string& imgSRc, int prefixType, int srcUid, std::vector<DocText>& docTexts);
};

#endif //SIMPLEREADER2_MOBI_UTIL_H
