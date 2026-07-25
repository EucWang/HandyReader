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
#include "xml2_ext.h"

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
        currentCssInfos.clear();
        isEmptyCss = false;
    }

    virtual ~epub_util() {
        book_id = 0;
        allChapters.clear();
        doc.ClearError();
        doc.Clear();
        currentSrc = "";
        currentCssInfos.clear();
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

    void valid_points(/*in,out*/std::vector<NavPoint> &points);

    int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) override;

    int32_t getWordCount(std::vector<ChapterCount> &wordCounts) override;

private:
    mutable std::mutex m_Mutex;
    mutable std::mutex m_Mutex2;
    mutable std::mutex m_Mutex3;
    mutable std::mutex m_Mutex4;
    unzFile bookzip;
    std::string currentSrc;
    /**
     * CSS parsed for [currentSrc]. Must be cached together with the `doc` / [currentSrc] pair:
     * getChapter() only re-reads and re-parses the spine document when spineSrc != currentSrc, so
     * a repeat call for the same document has to reuse this instead of applying no CSS at all
     * (which silently paginated the chapter without the publisher stylesheet).
     */
    std::vector<CssInfo> currentCssInfos;
    bool isEmptyCss;
    std::vector<BookManifest> manifests;
    std::vector<BookSpine> spines;
    std::vector<std::string> zipEntities;

    std::string opf_path;
    std::string ncx_path;
    std::string nav_path;
    int epubVersion;

    // 书的指纹字段已移除：本地环境 bookId 已能唯一确定书籍，cacheKey 只用
    // format + bookId + srcName(zipPath)，无需额外的 bookCrc。
    int epub_init();

    int parseOpfData(std::vector<NavPoint> &points);

    void handle_tags(JNIEnv *env, std::vector<DocText> &docTexts, std::vector<CssInfo> &cssInfos);

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

    /****
     * 将spine_name 转换成zip中的真实路径
     * @param spine_name
     * @return
     */
    std::string cover_to_zip_entity(const std::string &spine_name);

    int open_zip_and_entities();

    int load_zip_entity_data(const std::string &entity_name, std::string &output_data);

    int write_zip_entity_to_file(const std::string &entity_name, const std::string output_path);

    /***
     * 渲染期加载外链 CSS：按已解码的 <link> href 列表，从 manifest 重新匹配并加载。
     * 与真实章（type==0）的 parse_css_list() + <link> basename 匹配流程完全一致。
     * 入参是已 base_url_decode 的 href（由 extractCssByString 在切分期提取并缓存），
     * 本函数不再二次 decode，只做 extractFilename + basename 匹配。
     * @param linkHrefs  外链 <link> href 列表（已解码）
     * @return 合并后的外链 CSS 内容（各文件用 \n 分隔，已去重）
     */
    std::string loadExternalCss(const std::vector<std::string> &linkHrefs);

    // 判定 spine 项是否是封面/标题页（不参与切分）
    bool isLikelyCoverSrc(const std::string &src);

    /***
     * 渲染单个虚拟切分段为 docTexts（内联 CSS + 渲染期外链 CSS + 局部 tinyxml2 解析 + handle_tags）。
     * 抽取自 getChapter 虚拟分支，消除原 rebuild 路径的重复代码。
     * @param env
     * @param bodyContent      段 body 内容（HTML 片段）
     * @param cssText          内联 <style> CSS（来自缓存，可为空）
     * @param externalCssHrefs 外链 <link> href 列表（来自缓存，渲染期从 manifest 加载）
     * @param spineSrc         原始 spine 路径（用于 xml_ext::parse 的 src 参数）
     * @param docTexts         输出
     * @return 1 成功，0 失败
     */
    int renderSplitSegment(JNIEnv *env,
                           const std::string &bodyContent,
                           const std::string &cssText,
                           const std::vector<std::string> &externalCssHrefs,
                           const std::string &spineSrc,
                           std::vector<DocText> &docTexts);

    /***
     * 确保指定 zipPath 的切分缓存文件已存在（vsplit/<hash>.bin）。
     * 若已存在直接返回；否则 load+tidy+detectAndSplit 落盘。
     * 用于 getChapter(type==1)/getWordCount 在冷启动（文件缓存尚未生成）时按需生成。
     * 内存占用：仅 tidy 期间的临时全量正文，落盘后即释放，无常驻。
     * @param zipPath  被切分章节的 zip 内文件路径
     * @return 1=缓存就绪（已存在或新生成）；0=失败（文件不存在/太小/切分失败）
     */
    int ensureSplitOnDisk(const std::string &zipPath);

    void epub_release();
protected:
};


#endif //U_READER2_EPUB_UTIL_H
