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
#include <unordered_map>
#include <cstdint>
#include <fstream>
#include <filesystem>
#include <regex>
#include <random>
#include <vector>
#include <mutex>
#include <chrono>

#include "../util/log.h"
#include "bitmap_ext.h"
#include "app_ext.h"
#include "string_ext.h"
#include "file_ext.h"
#include "chapter_matcher.h"

#include "chapter_count.h"
#include "css_ext.h"
#include "css_info.h"
#include "doc_text.h"
#include "nav_point.h"
#include "tag_info.h"
#include "xml_ext.h"
#include "meta_data.h"


static constexpr size_t MAX_SPLIT_HTML_SIZE = 1024 * 1024 * 50;  // 50MB 防OOM
static constexpr size_t MIN_SPLIT_SEGMENTS = 3;                 //最小切分段数
static constexpr size_t MAX_SPLIT_SEGMENTS = 5000;              //最大切分段数
static constexpr size_t SPLIT_SIZE_THRESHOLD = 500 * 1024;      //500KB 触发
static constexpr size_t UNIFORM_SPLIT_CHARS = 150000;           //均匀切分每段字符

// 通用缓存文件格式版本
static constexpr uint32_t SPLIT_CACHE_MAGIC = 0x5653504C;  // "VSPL" 魔术字符，校验是否是缓存文件

// v3: 取消内存常驻缓存，文件格式改为 [头部|CSS|段索引表|各段独立正文]，
//     读章时按段直接 seek 读取，不再把全文读进内存（彻底规避内存常驻导致的 OOM）。
// v4: 修复 stripTagsAndCountBody 对无 <body> 标签的切分段返回 0 的缺陷（段字数恒为 0）。
//     bump 版本使旧 v3 缓存（charCount 全 0）失效，强制重新切分以修正字数统计。
// v5: tidy 下沉——切分段体改为 raw HTML（渲染期才 tidy），且段字数延迟统计（默认 0）。
//     语义双重变化，必须 bump 使旧 v4 缓存（tidied 段体 + 已统计字数）失效重切。
// v6: 修复 scanSplitPoints 闭合标签匹配 bug。原扫描器按开标签类型配对闭合（<div> 找
//     </div>），导致 <div> 包裹多个 <p> 时吞掉整段、章节标题漏检。改为匹配首个 </p>|</div>
//     （对标原正则语义）。切分结果变化，必须 bump 使旧 v5 缓存（漏检的稀疏分段）失效重切。
// v7: 修复标题长度判断用字节(size)而非字符数的 bug。中文 UTF-8 占 3 字节，「第3章 有技术的人」
//     = 9 字符/23 字节，被 size()<=15 误拒而漏检。改用 utf8Count 计字符数。切分结果变化，
//     必须 bump 使旧 v6 缓存（仍按字节漏检长标题）失效重切。
// v8: 彻底移除标题长度限制(原 <=15 字符)。长度限制两头不靠谱：漏检长标题、又挡不住短正文。
//     章节判定精确性由正则 ^锚定 +「章节卷后必须空白/结尾」保证。切分结果变化(检出更多章节)，
//     必须 bump 使旧 v7 缓存失效重切。
// v9: 外链 CSS 分离存储修复。cssText 此后仅含内联 <style>；新增 extCssHrefs 段（外链
//     <link> href 列表），外链 CSS 内容改在渲染期从 manifest 重新加载（与真实章流程一致，
//     根治切分期 cssSrc 为空导致外链 CSS 静默丢弃的 Bug）。cssText 语义变化，必须 bump
//     使旧 v8 缓存（外链 CSS 被丢弃的空 cssText）失效重切。
// v10: 新增 FR/DE/PT/ES/RU/HI/AR 七语种章节标题匹配（含罗马数字、Arabic-Indic、Devanagari 数字）。
//      切分结果变化（多语种书籍从均匀切分升级为真实标题切分，章节列表显示真实标题），
//      必须 bump 使旧 v9 缓存（按旧 5 类 matcher 切分的结果）失效重切。
static constexpr uint16_t SPLIT_CACHE_VERSION = 10;

// 单 spine 引用的外链 CSS 数量上限（读 cache 时先校验 count 合理再分配，防畸形/损坏的
// .bin 文件让读端分配出超大 vector。与 MAX_SPLIT_SEGMENTS / MAX_SPLIT_HTML_SIZE /
// titleLen>4096 同属统一的「读 cache 先校验长度」防御。正常 EPUB 单 spine 外链 CSS
// 通常 1~3 个，64 已留 20 倍余量。）
static constexpr uint32_t MAX_EXT_CSS_HREFS = 64;

// 格式码
enum SplitFormat : uint16_t {
    FMT_EPUB = 1,
    FMT_MOBI = 2,
    FMT_FB2 = 3,
    FMT_HTML = 4
};

/**
 * 单个切分段（格式无关：纯offset + length + title）
 * offset/length: 在 normalizedContent（切分时的临时全量正文）中的区间
 * fileOffset:    段独立正文落盘后在缓存文件中的绝对偏移（saveSplitCache 填充，
 *                loadSegmentFromFile 按此 seek 直接读该段，无需读全文）
 */
struct SplitSegment {
    size_t offset = 0;        // 在 normalizedContent 中的起始偏移
    size_t length = 0;        // 段长度
    std::string title;        // 章节标题
    size_t charCount = 0;     // 该段正文字数（P5: 切分时默认 0，getWordCount 读到 0 时按段正文现算）
    size_t picCount = 0;      // 该段图片数（P5: 同 charCount，延迟统计）
    uint64_t fileOffset = 0;  // 段正文在缓存文件中的绝对偏移（仅落盘后有意义）
};

/***
 * 执行扫描切分时的匹配信息
 */
struct RawMatch {
    size_t offset;
    size_t idx;
    std::string title;
    bool isVolume;
};

class book_util {
public:
    explicit book_util(long bookid, std::string bookpath) : run_flag(true), book_id(bookid), book_path(bookpath), isSingleSrc(false) {

    }

    virtual ~book_util() {
        run_flag = false;
        // 切分缓存全部持久化在文件（appCacheDir/vsplit/*.bin），无内存成员需清理。
    }

    virtual int getChapters(/*out*/std::vector<NavPoint> &points) = 0;

    virtual int getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) = 0;

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
    std::vector<CssInfo> cssInfos;  //缓存的章节的cssInfos集合

    std::vector<std::string> cssSrc;
    std::vector<NavPoint> allChapters;
    bool initStatus;
    long book_id;
    std::string book_path;
    MetaInfo meta_info;

    bool isSingleSrc;
    volatile bool run_flag;

    // 切分缓存设计（防 OOM 为唯一目标）：
    // 不再保留内存常驻缓存（旧版 m_splitCache 把整本 tidy 后正文常驻内存，本身就是 OOM 源）。
    // 切分时把每段小正文直接落盘到 appCacheDir/vsplit/<hash>.bin，读章时按段 seek 只取该段。
    // 全程无内存常驻全文，内存占用 = 当前正在解析的那一段（几百 KB 量级）。
    //
    // 并发模型：
    //   - 读（loadSegmentIndex/Body/Meta）：各用独立 ifstream + seek，对稳定文件并发读安全，不加锁。
    //   - 生成（detectAndSplit 的 scan+save）：必须串行化，否则 getChapter(持m_Mutex2) 与
    //     getWordCount(无锁) 并发首访同一本大书时会同时写同一缓存文件导致损坏。
    //     saveSplitCache 已用 tmp+rename 原子替换，保证读端不读半成品；m_cacheGenMutex 进一步
    //     避免双线程重复生成（省 CPU/内存）。锁仅在生成路径持有，不影响读路径并发。
    mutable std::mutex m_cacheGenMutex;

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

    /***
     * 构造缓存键：格式 + 书id + 被切分章节的源文件路径(zipPath)
     *
     * 设计说明：
     * - 本地环境 bookId 已能唯一确定书籍，无需额外书指纹（bookCrc 已移除）。
     * - srcName（EPUB=zipPath）可从 chapter.src 直接反推（cover_to_zip_entity），
     *   不依赖任何运行时状态，跨进程稳定：同书同文件 → 同 cacheKey。
     *
     * @param format
     * @param bookId
     * @param srcName 被切分章节的源文件路径（EPUB=zipPath，全路径稳定可复现）
     * @return
     */
    std::string buildSplitCacheKey(SplitFormat format,
                                   long bookId,
                                   const std::string &srcName) {
        std::string ret = std::to_string((int)format) + "_" + \
        std::to_string(bookId) + "_" + srcName;
        LOGI("%s: cacheKey is [%s]", __func__, ret.c_str());
        return ret;
    }

    /****
     * 通用的切分入口，子类提供 normalizedContent + cssText + cacheKey。
     * 切分结果（各段独立正文 + 索引）落盘到 vsplit/<hash>.bin，内存中不保留任何全文。
     *
     * @param cacheKey
     * @param format
     * @param bookId
     * @param bodyContent        body正文（临时全量，切完即释放）
     * @param cssText            内联 <style> CSS 内容（外链 CSS 不含，由 externalCssHrefs 单独传）
     * @param externalCssHrefs   外链 <link rel=stylesheet> 的 href 列表（渲染期从 manifest 加载）
     * @param outPoints
     * @return 1=切分并落盘成功；0=不切（太小/无切分点/超限）
     */
    int detectAndSplit(const std::string &cacheKey/*in*/,
                       SplitFormat format/*in*/,
                       long bookId/*in*/,
                       const std::string &bodyContent/*in*/,
                       const std::string &cssText/*in*/,
                       const std::vector<std::string> &externalCssHrefs/*in*/,
                       std::vector<NavPoint> &outPoints/*inout*/) {
        LOGI("%s:bookload:cacheKey=%s, contentSize=%zu", __func__, cacheKey.c_str(), bodyContent.size());
        auto _das_t0 = std::chrono::high_resolution_clock::now();

        //step1: 文件缓存已存在则直接构造 NavPoint（读索引即可，不读全文）
        std::vector<SplitSegment> segs;
        std::string cachedCss;
        std::vector<std::string> cachedExtHrefs;  // 命中路径不关心 href，丢弃
        if (1 == loadSplitIndex(cacheKey, format, segs, cachedCss, cachedExtHrefs)) {
            for (size_t i = 0; i < segs.size(); ++i) {
                NavPoint np;
                np.type = 1;
                np.splitSeq = (int)i;
                np.text = segs[i].title;
                outPoints.push_back(np);
            }
            LOGI("%s:bookload: file cache hit, %zu segments", __func__, segs.size());
            return 1;
        }

        //step2: 文件缓存未命中，进入生成路径。
        //加锁串行化：getChapter(持m_Mutex2) 与 getWordCount(无锁) 可能并发首访同一本书，
        //不串行化会双线程同时 scan+save 同一缓存文件（重复生成 + saveSplitCache 写冲突）。
        //锁仅在生成路径持有；命中路径(step1)与读路径(loadSegment*)不加锁，并发读不受影响。
        std::lock_guard<std::mutex> genLock(m_cacheGenMutex);
        // double-check：持锁后再查一次，第二个等锁线程醒来时若缓存已被首个线程生成则直接复用
        if (1 == loadSplitIndex(cacheKey, format, segs, cachedCss, cachedExtHrefs)) {
            for (size_t i = 0; i < segs.size(); ++i) {
                NavPoint np;
                np.type = 1;
                np.splitSeq = (int)i;
                np.text = segs[i].title;
                outPoints.push_back(np);
            }
            LOGI("%s: file cache hit (after lock), %zu segments", __func__, segs.size());
            return 1;
        }

        //上限保护：bodyContent 是临时全量，>50MB 一次性驻留有 OOM 风险，
        //此时放弃切分 fallback 原状（虽慢但不崩）。
        if (bodyContent.size() > MAX_SPLIT_HTML_SIZE) {  //防止OOM，基本上不会有50M的epub文件存在
            return 0;
        }
        if (bodyContent.size() < SPLIT_SIZE_THRESHOLD) { //小于500K， 太小了，也不用处理
            return 0;
        }
        std::vector<size_t> offsets;
        std::vector<std::string> titles;
        auto _scan_t0 = std::chrono::high_resolution_clock::now();
        int scanRet = scanSplitPoints(bodyContent, offsets, titles);
        auto _scan_t1 = std::chrono::high_resolution_clock::now();
        LOGI("%s:perf: scanSplitPoints=%lldms, scanRet=%d, offsets=%zu",
             __func__,
             std::chrono::duration_cast<std::chrono::milliseconds>(_scan_t1 - _scan_t0).count(),
             scanRet, offsets.size());
        if (1 != scanRet) { //扫描切分点失败
            return 0;
        }
        if (offsets.size() + 1 < MIN_SPLIT_SEGMENTS) { //切分点太少，不处理
            return 0;
        }
        //构造段表：N 个切分点 → N+1 个段
        auto _seg_t0 = std::chrono::high_resolution_clock::now();
        std::vector<SplitSegment> segments;
        for (size_t i = 0; i <= offsets.size(); ++i) {
            SplitSegment seg;
            seg.offset = (i == 0) ? 0 : offsets[i - 1];
            seg.length = ((i < offsets.size()) ? offsets[i] : bodyContent.size()) - seg.offset;
            // 段0标题留空（由子类设置为父章名）；其余段用切分点标题
            seg.title = (i == 0) ? "" : titles[i - 1];
            // ── P5: 字数/图片数延迟统计。切分时只记录 offset/length/title，
            //   charCount/picCount 保持默认 0。getWordCount 读到 0 时按 loadSegmentBody 现算。
            //   省去 54 段 substr+stripTags+count 的开销（切分热路径第三大开销）。
            //   首次 getWordCount 现算后经 updateChapterWordCountUseCase 写 DB，后续读 DB。 ──
            segments.push_back(seg);
        }
        if (segments.size() > MAX_SPLIT_SEGMENTS) {
            // 超出上限的段合并到最后一段，而非丢弃（丢弃会导致结尾内容永久不可见）
            auto &last = segments[MAX_SPLIT_SEGMENTS - 1];
            for (size_t i = MAX_SPLIT_SEGMENTS; i < segments.size(); ++i) {
                last.length += segments[i].length;
                last.charCount += segments[i].charCount;
                last.picCount += segments[i].picCount;
            }
            segments.resize(MAX_SPLIT_SEGMENTS);
        }
        auto _seg_t1 = std::chrono::high_resolution_clock::now();
        LOGI("%s:perf: buildSegments=%lldms, segments=%zu",
             __func__,
             std::chrono::duration_cast<std::chrono::milliseconds>(_seg_t1 - _seg_t0).count(),
             segments.size());

        //step3: 各段独立正文落盘（每段单独一小块，读章时按 fileOffset seek 直读该段）
        auto _save_t0 = std::chrono::high_resolution_clock::now();
        int saveRet = saveSplitCache(cacheKey, format, cssText, externalCssHrefs, bodyContent, segments);
        auto _save_t1 = std::chrono::high_resolution_clock::now();
        LOGI("%s:perf: saveSplitCache=%lldms, saveRet=%d, contentSize=%zu",
             __func__,
             std::chrono::duration_cast<std::chrono::milliseconds>(_save_t1 - _save_t0).count(),
             saveRet, bodyContent.size());
        if (1 != saveRet) {
            LOGE("%s: saveSplitCache failed, cacheKey=%s", __func__, cacheKey.c_str());
            return 0;
        }

        //step4: 构造NavPoint
        for(size_t i = 0; i < segments.size(); ++i) {
            NavPoint np;
            np.type = 1;
            np.splitSeq = (int)i;
            np.text = segments[i].title;
            outPoints.push_back(np);
        }
        auto _das_t1 = std::chrono::high_resolution_clock::now();
        LOGI("%s:perf: detectAndSplit_total=%lldms, outPoints=%zu",
             __func__,
             std::chrono::duration_cast<std::chrono::milliseconds>(_das_t1 - _das_t0).count(),
             outPoints.size());
        return 1;
    }

    // ──────────────────────────────────────────────────────────────────
    // 章节标题 matcher — 代码已移至 chapter_matcher.h（namespace chapter_matcher）
    //
    // 所有 matcher 函数已从本类抽取到独立头文件 chapter_matcher.h 中。
    // 本文件内部通过 chapter_matcher::matchChapterRe() 等方式调用。
    // ──────────────────────────────────────────────────────────────────


    /***
     * 通用的切分点扫描
     * @param content
     * @param outOffsets
     * @param outTitles
     * @return
     */
    int scanSplitPoints(const std::string &content/*in*/,
                        std::vector<size_t> &outOffsets/*out*/,
                        std::vector<std::string> &outTitles/*out*/) {
        std::vector<RawMatch> raw;

        //扫描 <p> 和 <div> 标签内容（章节标题可能出现在任一种容器中）
        //对 EPUB/MOBI/HTML 的内容通用；FB2 的 <p> 同样适用
        // ── P4: 用线性扫描器替代非贪婪正则（O(n²)→O(n)），严格对标原正则语义：
        //   匹配 <p 或 <div（大小写不敏感）+ 属性 [^>]* + > + 内层(.*?) + 首个 </p>/</div>。
        //   pos = '<' 在 content 中的偏移（与原 match.position() 语义一致），保证切分点 offset 不变。
        //   不处理嵌套（原 (.*?) 非贪婪到首个闭合，扫描器同样取首个同名闭合）。 ──
        auto findTagStart = [&content](size_t from) -> size_t {
            // 找下一个 <p 或 <div（大小写不敏感），返回 '<' 偏移
            for (size_t i = from; i + 2 < content.size(); ++i) {
                if (content[i] != '<') continue;
                char c1 = (char)tolower((unsigned char)content[i + 1]);
                char c2 = (char)tolower((unsigned char)content[i + 2]);
                if (c1 == 'p' && (c2 == '>' || c2 == ' ' || c2 == '\t' || c2 == '\n' || c2 == '\r')) {
                    return i;  // <p> / <p ...>
                }
                if (c1 == 'd' && c2 == 'i' && i + 3 < content.size() &&
                    tolower((unsigned char)content[i + 3]) == 'v') {
                    char c3 = (i + 4 < content.size()) ? (char)tolower((unsigned char)content[i + 4]) : ' ';
                    if (c3 == '>' || c3 == ' ' || c3 == '\t' || c3 == '\n' || c3 == '\r') {
                        return i;  // <div> / <div ...>
                    }
                }
            }
            return std::string::npos;
        };
        // ── 对标原正则 (.*?)</(?:p|div)> 的非贪婪语义：
        //   闭合匹配首个 </p> 或 </div>（无论开标签是 <p> 还是 <div>）。
        //   不按开标签类型配对——否则 <div> 包裹多个 <p> 时会吞掉整个 div，
        //   inner 文本超过 15 字符 → 章节标题漏检（第三章/第四章…全丢失）。
        //   返回 '<' 偏移；同时通过引用返回闭合标签长度（</p>=4, </div>=6）。
        auto findCloseTag = [&content](size_t from, size_t &outCloseLen) -> size_t {
            for (size_t i = from; i + 4 <= content.size(); ++i) {
                if (content[i] != '<') continue;
                // 先判 </p>（更短更常见，优先）
                if (tolower((unsigned char)content[i + 1]) == '/' &&
                    tolower((unsigned char)content[i + 2]) == 'p' &&
                    content[i + 3] == '>') {
                    outCloseLen = 4;
                    return i;
                }
                // 再判 </div>
                if (i + 6 <= content.size() &&
                    tolower((unsigned char)content[i + 1]) == '/' &&
                    tolower((unsigned char)content[i + 2]) == 'd' &&
                    tolower((unsigned char)content[i + 3]) == 'i' &&
                    tolower((unsigned char)content[i + 4]) == 'v' &&
                    content[i + 5] == '>') {
                    outCloseLen = 6;
                    return i;
                }
            }
            return std::string::npos;
        };

        size_t scanPos = 0;
        while (scanPos < content.size()) {
            size_t lt = findTagStart(scanPos);
            if (lt == std::string::npos) break;
            // 找标签的 '>'（属性结束）
            size_t gt = content.find('>', lt);
            if (gt == std::string::npos) break;
            size_t innerStart = gt + 1;
            // 找首个 </p> 或 </div>（对标 (.*?) 非贪婪语义，不按开标签类型配对）
            size_t closeLen = 4;
            size_t closeLt = findCloseTag(innerStart, closeLen);
            if (closeLt == std::string::npos) {
                scanPos = gt + 1;
                continue;
            }
            // p/div 标签的内部内容
            std::string inner = content.substr(innerStart, closeLt - innerStart);
            // pos 语义 = 原 regex match.position()（即 '<' 在 content 中的偏移）
            size_t pos = lt;
            std::string clean = string_ext::stripTags(inner);
            string_ext::trim(clean);
            // 不再限制标题长度。原 clean.size()<=15 有两个缺陷：
            // 1) size() 返回字节数（中文 UTF-8 占 3 字节），「第3章 有技术的人」(9字符/23字节)被误拒；
            // 2) 即使按字符数，真实长标题（如「第三章 关于那些被遗忘的往事」）也会被漏检。
            // 章节判定的精确性由下方正则的 ^ 锚定 +「章节卷后必须空白/结尾」约束保证，
            // 正文「第三章的...」因「章」后跟「的」不会匹配，无需长度启发式。
            if (!clean.empty()) {
                bool isVol = false;
                bool matched = false;
                if (chapter_matcher::matchChapterRe(clean)) { //匹配中文的标题
                    matched = true;
                    isVol = (clean.find(u8"卷") != std::string::npos);
                } else if (chapter_matcher::matchSpecialRe(clean)) { //匹配中文繁体的标题
                    matched = true;
                } else if (chapter_matcher::matchEnRe(clean)) {  //匹配英文的标题
                    matched = true;
                } else if (chapter_matcher::matchJpRe(clean)) {  //匹配日语的标题
                    matched = true;
                } else if (chapter_matcher::matchKrRe(clean)) {  //匹配韩语的标题
                    matched = true;
                } else if (chapter_matcher::matchLatinRe(clean)) {  //匹配法/德/葡/西的标题（含罗马数字）
                    matched = true;
                } else if (chapter_matcher::matchRussianRe(clean)) {  //匹配俄语的标题
                    matched = true;
                } else if (chapter_matcher::matchHindiRe(clean)) {  //匹配印地语的标题
                    matched = true;
                } else if (chapter_matcher::matchArabicRe(clean)) {  //匹配阿拉伯语的标题
                    matched = true;
                }

                if (matched) { //匹配到了，则保存匹配结果到raw中
                    raw.push_back({
                        pos,  //切片章节的位置
                        raw.size(), //当前的索引
                        clean,     //章节的标题
                        isVol //是否是卷
                    });
                }
            }
            // 跳过闭合标签，继续扫描（对标原 str = match.suffix()）
            size_t closeEnd = closeLt + closeLen;
            scanPos = (closeEnd > scanPos) ? closeEnd : scanPos + 1;
        }

        //去重， 相邻间距< 3段时， 章优先于卷的
        std::vector<RawMatch> dedup; //去重之后的结果
        for(auto &rMatchItem : raw) { //对匹配结果进行遍历
            if (!dedup.empty() && rMatchItem.idx - dedup.back().idx < 3) {
                if (rMatchItem.isVolume && !dedup.back().isVolume) { //当前是卷，但是上一个遍历位置不是卷
                    continue;
                }
                if(dedup.back().isVolume && !rMatchItem.isVolume) {
                    dedup.pop_back();
                    dedup.push_back(rMatchItem);
                    continue;
                }
            }
            dedup.push_back(rMatchItem);
        }

        //频率合理性校验：防止正则在正文里误匹配出大量假章节标题（如正文反复提到"第三章"）。
        //若平均段间距过小（<500字符，说明切出的都是碎片）或过大（>50万字符，说明只匹到几个噪声点），
        //判定为误匹配，清空后走均匀切分兜底。这是正则方案的安全网，不能去掉。
        if (dedup.size() >= 2) {
            size_t avgGap = content.size() / dedup.size();
            if (avgGap < 500 || avgGap > 500000) {
                LOGW("%s: avgGap=%zu 误匹配，降级均匀切分", __func__, avgGap);
                dedup.clear();
            }
        }

        //块边界回溯（切在 </p> 之后）
        //── P6: 用单次正向扫描替代逐个 rfind，彻底消除 O(n²)。
        //   原代码对每个 dedup 项调 content.rfind("</p>", cutPos)：
        //   rfind 在 NDK libc++ 上对 10.8MB 巨串反向扫描，CPU 预取完全失效，
        //   1360 次 × ~70ms = 96 秒（实测 block_boundary=96070ms）。
        //   dedup 已按 offset 升序排列，用 content.find("</p>", cursor) 单向前进，
        //   跟踪「最近见过的 </p>」，cursor 跨所有项单调递增，总扫描量 = O(content.size())。
        //   语义完全等价：lastCloseP == content.rfind("</p>", cutPos) 的返回值。
        //   （lastCloseP = 最后一个起始位置 ≤ cutPos 的 "</p>"）
        if (!dedup.empty()) {
            size_t cursor = 0;           // find 游标，单调递增
            size_t lastCloseP = std::string::npos;  // 游标推进过程中见过的最后一个 "</p>"
            for (auto &item : dedup) {
                size_t cutPos = item.offset;
                // 前进游标，记录所有 ≤ cutPos 的 "</p>"，停在第一个 > cutPos 处
                while (true) {
                    size_t found = content.find("</p>", cursor);
                    if (found == std::string::npos || found > cutPos) break;
                    lastCloseP = found;
                    cursor = found + 4;
                }
                size_t cutAdj = cutPos;
                if (lastCloseP != std::string::npos && lastCloseP + 4 <= cutPos) {
                    cutAdj = lastCloseP + 4;
                }
                outOffsets.push_back(cutAdj);
                outTitles.push_back(item.title);
            }
            return 1;
        }

        //均匀切分兜底
        //每隔15W隔字符进行切分，计算分割多少段
        //── P6: 同样用正向 find 单次扫描替代逐个 rfind（见上方 block_boundary 注释）。
        //   各切分点 i*150000 升序，cursor 单调前进；额外用 searchFrom 窗口过滤掉
        //   离 pos 太远的 </p>（防段长严重不均），等价于原 closeP>=searchFrom 判断。
        size_t count = content.size() / UNIFORM_SPLIT_CHARS;
        // 分割段小于2，则最小就是2
        if (count < MIN_SPLIT_SEGMENTS - 1) {
            count = MIN_SPLIT_SEGMENTS - 1;
        }
        size_t lastCut = 0;
        size_t cursor = 0;           // find 游标，单调递增
        size_t lastCloseP = std::string::npos;  // 游标推进过程中见过的最后一个 "</p>"
        for (size_t i = 1; i <= count; i++) {
            size_t pos = i * UNIFORM_SPLIT_CHARS;
            if (pos > content.size()) pos = content.size();
            // 块边界回溯：避免切在 <p> 内部导致该段首部是未闭合标签碎片
            // （tinyxml2 解析未闭合标签可能丢内容或返回空）。仅在合理范围(±5000字符)内回溯，
            // 防止极端情况下回溯过远导致段长严重不均。
            size_t searchFrom = (pos >= 5000) ? (pos - 5000) : lastCut;
            // 前进游标，记录所有 ≤ pos 的 "</p>"
            while (true) {
                size_t found = content.find("</p>", cursor);
                if (found == std::string::npos || found > pos) break;
                lastCloseP = found;
                cursor = found + 4;
            }
            if (lastCloseP != std::string::npos && lastCloseP >= searchFrom && lastCloseP + 4 <= pos) {
                pos = lastCloseP + 4;
            }
            if (pos > lastCut) {
                outOffsets.push_back(pos);
                //均匀切分无法获知语言，用纯数字序号（UI 层可按语言习惯格式化显示）
                outTitles.push_back(std::to_string(i));
            }
            lastCut = pos;
        }
        return 1;
    }

    /**
     * 读缓存文件头部 + 段索引表（不读各段正文）。供 detectAndSplit 命中检测、
     * getWordCount 取段字数、getChapter 取 cssText / 定位段正文偏移。
     *
     * 文件布局（v3 起，v9 在 cssText 后新增 extCssHrefs 段）：
     *   [magic:4][version:2][fmt:2]
     *   [cssLen:4][cssText:cssLen]                 ← 仅内联 <style>（v9 起外链 CSS 不再烘入）
     *   [extCssCount:4][per-href: hrefLen:4 + hrefBytes:hrefLen]   ← v9 新增：外链 <link> href 列表
     *   [segCount:4]
     *   对每段: [fileOffset:8][length:8][charCount:8][picCount:8][titleLen:2][title:titleLen]
     *   [seg0 正文][seg1 正文]...   ← 紧跟索引表，各段顺序写入
     *
     * @return 1=成功；0=文件不存在/格式不符/损坏
     */
    int loadSplitIndex(const std::string &cacheKey,
                       SplitFormat format,
                       std::vector<SplitSegment> &outSegs,
                       std::string &outCss,
                       std::vector<std::string> &outExternalCssHrefs) {
        try {
            std::string path = getSplitCachePath(cacheKey);
            if (!std::filesystem::exists(path)) return 0;

            std::ifstream ifs(path, std::ios::binary);
            if (!ifs.is_open()) return 0;

            uint32_t magic; uint16_t version; uint16_t fmt;
            ifs.read((char*)&magic, 4);
            if (magic != SPLIT_CACHE_MAGIC) return 0;
            ifs.read((char*)&version, 2);
            if (version != SPLIT_CACHE_VERSION) return 0;  // 版本不符，交由上层重建
            ifs.read((char*)&fmt, 2);
            if (fmt != (uint16_t)format) return 0;

            uint32_t cssLen;
            ifs.read((char*)&cssLen, 4);
            if (cssLen > MAX_SPLIT_HTML_SIZE) {
                LOGE("%s: cssLen=%u exceeds limit", __func__, cssLen);
                return 0;
            }
            if (cssLen > 0) {
                outCss.resize(cssLen);
                ifs.read(&outCss[0], cssLen);
            }

            // v9: 外链 <link> href 列表。无条件 clear（S3）：loadSplitIndex 被反复调用，
            // 必须在读取前清空，防止上一次调用的残留污染本次结果。
            outExternalCssHrefs.clear();
            uint32_t extCssCount;
            ifs.read((char*)&extCssCount, 4);
            if (extCssCount > MAX_EXT_CSS_HREFS) {
                LOGE("%s: extCssCount=%u exceeds limit", __func__, extCssCount);
                return 0;
            }
            for (uint32_t i = 0; i < extCssCount; ++i) {
                uint32_t hrefLen;
                ifs.read((char*)&hrefLen, 4);
                if (hrefLen > 4096) { LOGE("%s: hrefLen=%u invalid", __func__, hrefLen); return 0; }
                std::string href;
                if (hrefLen > 0) {
                    href.resize(hrefLen);
                    ifs.read(&href[0], hrefLen);
                }
                outExternalCssHrefs.push_back(std::move(href));
            }

            uint32_t segCount;
            ifs.read((char*)&segCount, 4);
            if (segCount == 0 || segCount > MAX_SPLIT_SEGMENTS) {
                LOGE("%s: segCount=%u invalid", __func__, segCount);
                return 0;
            }
            outSegs.clear();
            outSegs.reserve(segCount);
            for (uint32_t i = 0; i < segCount; i++) {
                SplitSegment seg;
                uint64_t fileOff, len, charCount, picCount;
                uint16_t titleLen;
                ifs.read((char*)&fileOff, 8);
                ifs.read((char*)&len, 8);
                ifs.read((char*)&charCount, 8);
                ifs.read((char*)&picCount, 8);
                ifs.read((char*)&titleLen, 2);
                seg.fileOffset = fileOff;
                seg.length = (size_t)len;
                seg.charCount = (size_t)charCount;
                seg.picCount = (size_t)picCount;
                if (titleLen > 0) {
                    if (titleLen > 4096) { LOGE("%s: titleLen=%u invalid", __func__, titleLen); return 0; }
                    seg.title.resize(titleLen);
                    ifs.read(&seg.title[0], titleLen);
                }
                outSegs.push_back(seg);
            }
            return ifs.fail() ? 0 : 1;
        } catch (const std::exception &e) {
            LOGE("%s: exception %s", __func__, e.what());
            return 0;
        }
    }

    /**
     * 按段序号从缓存文件读取该段正文（seek 直读，不读其它段）。
     * 内存占用 = 该段长度（通常几十~几百 KB），无全文常驻。
     */
    int loadSegmentBody(const std::string &cacheKey,
                        SplitFormat format,
                        int seq,
                        std::string &outBody) {
        std::vector<SplitSegment> segs;
        std::string css;
        std::vector<std::string> dummyHrefs;  // loadSegmentBody 不关心外链 href，丢弃
        if (1 != loadSplitIndex(cacheKey, format, segs, css, dummyHrefs)) return 0;
        if (seq < 0 || seq >= (int)segs.size()) return 0;

        const SplitSegment &seg = segs[seq];
        if (seg.length == 0 || seg.length > MAX_SPLIT_HTML_SIZE) return 0;

        try {
            std::string path = getSplitCachePath(cacheKey);
            std::ifstream ifs(path, std::ios::binary);
            if (!ifs.is_open()) return 0;
            ifs.seekg((std::streamoff)seg.fileOffset, std::ios::beg);
            outBody.resize(seg.length);
            ifs.read(&outBody[0], seg.length);
            return ifs.fail() ? 0 : 1;
        } catch (const std::exception &e) {
            LOGE("%s: exception %s", __func__, e.what());
            return 0;
        }
    }

    /**
     * 取段字数/图片数（getWordCount 用，只读索引，不读正文）。
     * @return 1=命中；0=缓存不存在/seq越界
     */
    int loadSegmentMeta(const std::string &cacheKey,
                        SplitFormat format,
                        int seq,
                        size_t &outCharCount,
                        size_t &outPicCount) {
        std::vector<SplitSegment> segs;
        std::string css;
        std::vector<std::string> dummyHrefs;  // loadSegmentMeta 不关心外链 href，丢弃
        if (1 != loadSplitIndex(cacheKey, format, segs, css, dummyHrefs)) return 0;
        if (seq < 0 || seq >= (int)segs.size()) return 0;
        outCharCount = segs[seq].charCount;
        outPicCount = segs[seq].picCount;
        return 1;
    }

    /** 快速判断文件缓存是否存在且版本有效（detectAndSplit/parseOpfData 命中检测用） */
    bool hasSplitCache(const std::string &cacheKey, SplitFormat format) {
        std::vector<SplitSegment> segs; std::string css;
        std::vector<std::string> dummyHrefs;  // hasSplitCache 不关心外链 href，丢弃
        return 1 == loadSplitIndex(cacheKey, format, segs, css, dummyHrefs);
    }

    /****
     * 写缓存文件：头部 + CSS + 段索引表(含 fileOffset) + 各段独立正文。
     * 两趟写入（O(n)，无 seek 回填）：
     *   pass1: 先算出各段 fileOffset（头部+CSS+索引表大小已知，正文区紧随其后顺序排列）
     *   pass2: 写索引表（fileOffset 已正确）→ 写各段正文
     * 写到 <path>.tmp 再 rename，保证读端要么看到旧文件要么看到完整新文件，不会读到半成品。
     *
     * @param cacheKey
     * @param format
     * @param cssText           内联 <style> CSS 内容
     * @param externalCssHrefs  外链 <link> href 列表（v9 新增，渲染期从 manifest 加载）
     * @param normalizedContent  切分时的临时全量正文（仅用于按 [offset,length] 切出各段，写完即释放）
     * @param segments           段表（offset/length/title/charCount/picCount 已填，fileOffset 由本函数填）
     * @return 1=成功；0=失败
     */
    int saveSplitCache(const std::string &cacheKey,
                       SplitFormat format,
                       const std::string &cssText,
                       const std::vector<std::string> &externalCssHrefs,
                       const std::string &normalizedContent,
                       std::vector<SplitSegment> &segments/*inout: 回填 fileOffset*/) {
        try {
            // 边界校验：写正文前确认每段区间落在 normalizedContent 内，防 OOB 读
            for (const auto &seg : segments) {
                if (seg.offset > normalizedContent.size() ||
                    seg.length > normalizedContent.size() - seg.offset) {
                    LOGE("%s: seg OOB offset=%zu length=%zu contentSize=%zu",
                         __func__, seg.offset, seg.length, normalizedContent.size());
                    return 0;
                }
            }

            std::string path = getSplitCachePath(cacheKey);
            std::string dir = path.substr(0, path.find_last_of("/"));
            if (!std::filesystem::exists(dir)) {
                std::filesystem::create_directories(dir);
            }
            std::string tmpPath = path + ".tmp";

            std::ofstream ofs(tmpPath, std::ios::binary | std::ios::trunc);
            if (!ofs.is_open()) return 0;

            //头部
            uint32_t magic = SPLIT_CACHE_MAGIC;
            uint16_t version = SPLIT_CACHE_VERSION;
            uint16_t fmt = (uint16_t)format;
            ofs.write((char*)&magic, 4);
            ofs.write((char*)&version, 2);
            ofs.write((char*)&fmt, 2);

            //CSS（仅内联 <style>）
            uint32_t cssLen = (uint32_t)cssText.size();
            ofs.write((char*)&cssLen, 4);
            if (cssLen > 0) ofs.write(cssText.data(), cssLen);

            //v9: 外链 <link> href 列表。S2 截断一致性——count、循环上界、下标三方都用截断后的值，
            //   避免「写 count=截断值却循环原 size」或「count 写原值读侧拒绝」的失配。
            uint32_t extCssCount = (uint32_t)std::min(externalCssHrefs.size(),
                                                      (size_t)MAX_EXT_CSS_HREFS);
            ofs.write((char*)&extCssCount, 4);
            for (uint32_t i = 0; i < extCssCount; ++i) {
                uint32_t hrefLen = (uint32_t)externalCssHrefs[i].size();
                ofs.write((char*)&hrefLen, 4);
                if (hrefLen > 0) ofs.write(externalCssHrefs[i].data(), hrefLen);
            }

            uint32_t segCount = (uint32_t)segments.size();
            ofs.write((char*)&segCount, 4);

            // pass1: 计算各段 fileOffset。
            // 索引表大小 = Σ(34 + titleLen[i])，正文区从索引表末尾开始顺序排列。
            uint64_t indexSize = 0;
            for (auto &seg : segments) indexSize += 34ULL + (uint64_t)seg.title.size();
            uint64_t bodyCursor = (uint64_t)ofs.tellp() + indexSize;
            for (auto &seg : segments) {
                seg.fileOffset = bodyCursor;
                bodyCursor += seg.length;
            }

            // pass2: 写索引表（fileOffset 已正确）
            for (auto &seg : segments) {
                uint64_t fileOff = seg.fileOffset;
                uint64_t len = seg.length;
                uint64_t charCount = seg.charCount;
                uint64_t picCount = seg.picCount;
                uint16_t titleLen = (uint16_t)seg.title.size();
                ofs.write((char*)&fileOff, 8);
                ofs.write((char*)&len, 8);
                ofs.write((char*)&charCount, 8);
                ofs.write((char*)&picCount, 8);
                ofs.write((char*)&titleLen, 2);
                if (titleLen > 0) ofs.write(seg.title.data(), titleLen);
            }
            // 写各段正文（区间已校验，安全）
            for (auto &seg : segments) {
                if (seg.length > 0) {
                    ofs.write(normalizedContent.data() + seg.offset, (std::streamsize)seg.length);
                }
            }

            ofs.flush();
            bool fail = ofs.fail();
            ofs.close();
            if (fail) {
                std::error_code ec;
                std::filesystem::remove(tmpPath, ec);
                return 0;
            }
            // 原子替换：同文件系统 rename 是原子的，读端不会看到半成品
            std::error_code ec;
            std::filesystem::rename(tmpPath, path, ec);
            if (ec) {
                LOGE("%s: rename failed: %s", __func__, ec.message().c_str());
                std::filesystem::remove(tmpPath, ec);
                return 0;
            }
            return 1;
        } catch (const std::exception &e) {
            LOGE("%s: exception %s", __func__, e.what());
            return 0;
        }
    }

    /****
     * 获取大章节缓存路径
     * @param cacheKey
     * @return
     */
    std::string getSplitCachePath(const std::string &cacheKey) {
        std::hash<std::string> hasher;
        size_t hashValue = hasher(cacheKey);
        std::string path = app_ext::appCacheDir + "/vsplit/" + std::to_string(hashValue) + ".bin";
        LOGI("%s: result path is [%s]", __func__ , path.c_str());
        return path;
    }
};

#endif //U_READER2_BOOK_UTIL_H
