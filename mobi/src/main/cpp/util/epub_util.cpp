//
// Created by MAC on 2025/6/18.
//

#include "epub_util.h"

const std::string epub_zfile_container = "META-INF/container.xml";
const std::string epub_zfile_mimetype = "mimetype";
//const std::string epub_zfile_toc_ncx = "toc.ncx";
const std::string epub_zfile_nav_xhtml = "nav.xhtml";   //epub3 中 可能会用这个文件代替toc.ncx

namespace {
struct UnzGuard {
    unzFile uf;
    explicit UnzGuard(unzFile f) : uf(f) {}
    ~UnzGuard() { if (uf) unzClose(uf); }
    UnzGuard(const UnzGuard&) = delete;
    UnzGuard& operator=(const UnzGuard&) = delete;
};
} // namespace

int epub_util::open_zip_and_entities() {
    if (!run_flag) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(m_Mutex4);
    int ret = 0;
    do {
        bookzip = unzOpen(book_path.c_str());
        if (bookzip == nullptr) {
            LOGE("%s cannot open file[%s]", __func__, book_path.c_str());
            ret = 0;
            break;
        }

        unz_global_info gi; //获取zip文件中的条目数
        int err = unzGetGlobalInfo(bookzip, &gi);
        if (err != UNZ_OK || gi.number_entry <= 0) {
            LOGE("%s cannot get zip file info", __func__);
            unzClose(bookzip);
            ret = 0;
            break;
        }

        std::vector<std::string> zipfilenames = zip_ext::inner_zip_files(bookzip);
        if (!zipfilenames.empty()) {
            zipEntities.clear();
            zipEntities.insert(zipEntities.end(), zipfilenames.begin(), zipfilenames.end());
        }
        ret = 1;
    } while(false);
    return ret;
}

int epub_util::write_zip_entity_to_file(const std::string &entity_name, const std::string output_path) {
    if (!initStatus || !run_flag) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(m_Mutex4);
    return zip_ext::write_zip_item_to_file(bookzip, entity_name, output_path);
}

int epub_util::load_zip_entity_data(const std::string &entity_name, std::string &output_data) {
    if (!run_flag) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(m_Mutex4);
    int retry = 3;
    bool flag = false;
    do {
        if (!run_flag) {
            retry = 0;
            flag = false;
            break;
        }
        if (retry != 3) {
            LOGD("%s retry[%d] and await 100ms", __func__, retry);
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
        }
        if (1 != zip_ext::read_zip_file(bookzip, entity_name, output_data)) {
            LOGE("%s read [%s] failed", __func__, entity_name.c_str());
            flag = false;
            retry--;
        } else {
            flag = true;
        }
    } while(retry > 0 && !flag);
    if (flag) {
        return 1;
    } else {
        return 0;
    }
}

void epub_util::epub_release() {
    LOGI("%s:invoke", __func__);
    std::lock_guard<std::mutex> lock(m_Mutex4);
    if (initStatus) {
        unzClose(bookzip);
        bookzip = nullptr;
        initStatus = false;
    }
    LOGI("%s:invoke done", __func__);
}

/***
 * 渲染期加载外链 CSS：按已解码的 <link> href 列表，从 manifest 重新匹配并加载。
 * 与真实章（type==0）的 parse_css_list() + <link> basename 匹配流程完全一致：
 *   1. parse_css_list() 从 manifests 填充 cssSrc（幂等，latch 保护，首次后免费）
 *   2. 对每个入参 href 取 basename，在 cssSrc 里子串匹配
 *   3. cover_to_zip_entity 解析 zip 路径 → load_zip_entity_data 读 CSS → 合并
 * 入参是已 base_url_decode 的 href（extractCssByString 在切分期解码后缓存），
 * 本函数不再二次 decode，只做 extractFilename + basename 匹配。
 * @param linkHrefs  外链 <link> href 列表（已解码）
 * @return 合并后的外链 CSS 内容（各文件用 \n 分隔，已按 zip 路径去重）
 */
std::string epub_util::loadExternalCss(const std::vector<std::string> &linkHrefs) {
    std::string combined;
    // 入口先确保 cssSrc 已从 manifest 填充（幂等：latch 后免费）
    parse_css_list();
    if (cssSrc.empty() || linkHrefs.empty()) return combined;
    std::vector<std::string> loadedPaths;  // 按 zip 路径去重
    for (const auto &href : linkHrefs) {
        std::string cssFilename = file_ext::extractFilename(href);
        if (cssFilename.empty()) continue;
        for (auto &cssHref : cssSrc) {
            if (cssHref.find(cssFilename) != std::string::npos) {
                std::string cssZipPath = cover_to_zip_entity(cssHref);
                if (cssZipPath.empty()) break;
                // 去重
                bool alreadyLoaded = false;
                for (auto &loaded : loadedPaths) {
                    if (loaded == cssZipPath) { alreadyLoaded = true; break; }
                }
                if (alreadyLoaded) break;
                std::string cssData;
                if (1 == load_zip_entity_data(cssZipPath, cssData) && !cssData.empty()) {
                    combined += cssData + "\n";
                    loadedPaths.push_back(cssZipPath);
                }
                break;  // 首个 manifest 匹配项胜出
            }
        }
    }
    return combined;
}

/***
 * 判定 spine 项是否是封面/标题页（不参与切分）
 */
bool epub_util::isLikelyCoverSrc(const std::string &src) {
    std::string lower = string_ext::to_lower(src);
    return lower.find("titlepage") != std::string::npos
        || lower.find("cover") != std::string::npos;
}

int epub_util::renderSplitSegment(JNIEnv *env,
                                  const std::string &bodyContent,
                                  const std::string &cssText,
                                  const std::vector<std::string> &externalCssHrefs,
                                  const std::string &spineSrc,
                                  std::vector<DocText> &docTexts) {
    // ── P1: tidy 下沉。切分期不再 tidy，段内容是 raw HTML 片段。
    //        用 body-only tidy 修复畸形 HTML（未闭合标签、<br>→<br/>），保证 tinyxml2 可解析。
    //        必须用 TidyBodyOnly：普通 tidy 会输出完整文档，二次包裹导致嵌套 <html>。
    //        单段通常 ~150KB，tidy 耗时 ~30ms，懒加载（翻到才跑），用户无感。 ──
    std::string tidiedBody = bodyContent;
    if (1 != tidyh5_ext::tidy_html_body_only(tidiedBody)) {
        LOGW("%s: tidy failed, fallback to raw parse", __func__);
        tidiedBody = bodyContent;  // tidy 失败则用 raw 试解析（部分段可能本就良构）
    }

    // 组装完整 HTML（CSS 内联，解决样式丢失）
    std::string segHtml = "<html><head>";
    if (!cssText.empty()) segHtml += "<style>" + cssText + "</style>";
    segHtml += "</head><body>" + tidiedBody + "</body></html>";

    // 局部 doc 解析片段，不碰 this->doc / currentSrc（避免污染其他章节缓存）
    tinyxml2::XMLDocument localDoc;
    if (localDoc.Parse(segHtml.c_str(), segHtml.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s: localDoc.Parse failed", __func__);
        return 0;
    }

    std::vector<CssInfo> cssInfos;
    if (!cssText.empty()) {
        std::string cssCopy = cssText; // parse_css 要求非 const 引用
        css_ext::parse_css(cssCopy, cssInfos);
    }

    // ── v9: 渲染期从 manifest 重新加载外链 CSS（与真实章 type==0 流程一致）。
    //   cssText 现仅含内联 <style>；外链 CSS 的 href 在切分期提取并缓存，
    //   此处按 href 从 manifest 匹配 + zip 加载 + parse_css 追加到同一 cssInfos。
    //   降级策略（B5）：loadExternalCss 返回空（zip 损坏/run_flag=false/无 manifest 匹配）
    //   时只记 LOGW，不中断渲染——段仍可显示，只是无外链样式。 ──
    if (!externalCssHrefs.empty()) {
        std::string extCss = loadExternalCss(externalCssHrefs);
        if (extCss.empty()) {
            LOGW("%s: loadExternalCss returned empty (hrefs=%zu), render without external CSS",
                 __func__, externalCssHrefs.size());
        } else {
            css_ext::parse_css(extCss, cssInfos);
        }
    }

    int flagAdd = 1; // 虚拟章 anchorId 为空，全段输出
    tinyxml2::XMLElement *childEle = xml_ext::getStartElement(
        localDoc.RootElement(), &flagAdd, "");
    if (childEle != nullptr) {
        std::string emptyAnchor, emptyEnd;
        std::string spineSrcCopy = spineSrc; // parse 要求非 const 引用
        xml_ext::parse(childEle, docTexts, emptyAnchor, emptyEnd, &flagAdd, spineSrcCopy);
        if (!docTexts.empty()) {
            handle_tags(env, docTexts, cssInfos);
        }
    }
    return 1;
}


int epub_util::ensureSplitOnDisk(const std::string &zipPath) {
    std::string cacheKey = buildSplitCacheKey(FMT_EPUB, book_id, zipPath);
    // 文件缓存已存在则直接返回（不读全文，只校验头部）
    if (hasSplitCache(cacheKey, FMT_EPUB)) {
        return 1;
    }
    LOGI("%s:bookload: generating cache for zipPath=[%s]", __func__, zipPath.c_str());

    // 加载原始 HTML
    std::string rawHtml;
    if (1 != load_zip_entity_data(zipPath, rawHtml)) {
        LOGE("%s:bookload: load_zip_entity_data failed for [%s]", __func__, zipPath.c_str());
        return 0;
    }
    // 大小阈值判断
    if (rawHtml.size() < SPLIT_SIZE_THRESHOLD) {
        LOGW("%s:bookload: rawHtml too small (%zu), skip", __func__, rawHtml.size());
        return 0;
    }
    // ── P3: 不再 tidy（与 parseOpfData 切分块一致）。tidy 下沉到 renderSplitSegment。 ──
    std::string bodyContent = string_ext::extractBodyContent(rawHtml);
    std::vector<std::string> extHrefs;
    std::string cssText = string_ext::extractCssByString(rawHtml, extHrefs);  // cssText=内联，extHrefs=外链 href

    // 执行切分并落盘（detectAndSplit 内部会再次 hasSplitCache，命中则跳过）
    std::vector<NavPoint> parts;
    if (1 == detectAndSplit(cacheKey, FMT_EPUB, book_id, bodyContent, cssText, extHrefs, parts)) {
        LOGI("%s:bookload: cache generated, %zu segments", __func__, parts.size());
        return 1;
    }
    LOGW("%s:bookload: detectAndSplit returned 0 (file too large or no split points)", __func__);
    return 0;
}


/****
 * 将spine_name 转换成zip中的真实路径
 * @param spine_name
 * @return
 */
std::string epub_util::cover_to_zip_entity(const std::string &spine_name) {
//    LOGD("%s invoke", __func__);
    std::string ret = spine_name;
    if (spine_name.empty()) {
        return ret;
    }
    if (this->zipEntities.empty()) {
        return ret;
    }

    std::string spine = spine_name;
    if (string_ext::startWith(spine, "/")) {
        spine = spine.substr(1);
    } else if (string_ext::startWith(spine, "./")) {
        spine = spine.substr(2);
    } else if (string_ext::startWith(spine, "../")) {
        spine = spine.substr(3);
    }

    auto it = std::find_if(zipEntities.begin(), zipEntities.end(), [=](std::string &item){
        return !string_ext::endsWith(item, "/") &&
         !string_ext::endsWith(spine, "/") &&
         item.find(spine) != std::string::npos;
    });
    if (it != zipEntities.end()) {
        ret = (*it);
    }
//    LOGD("%s invoke done", __func__);
    return ret;
}

int epub_util::epub_init() {
    LOGI("%s:invoke", __func__);
    if (book_id == 0L || book_path.empty()) {
        return 0;
    }

    if (1 != open_zip_and_entities()) {
        return 0;
    }

    //解析"META-INF/container.xml" ，获得opf路径
    std::string container_data;
    if (1 != load_zip_entity_data(epub_zfile_container, container_data)) {
        return 0;
    }
    tinyxml2::XMLDocument doc;
    doc.ClearError();
    doc.Clear();
    if (doc.Parse(container_data.c_str(), container_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse container.xml", __func__);
        return 0;
    }
    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse container.xml, no root element", __func__);
        return 0;
    }

    auto rootfiles = xml_ext::firstChildEleWithTwoName(root, "rootfiles", "opf:rootfiles");
    if (rootfiles == nullptr) {
        LOGE("%s failed parse container.xml, no rootfiles element", __func__);
        return 0;
    }
    auto rootfile = xml_ext::firstChildEleWithTwoName(rootfiles, "rootfile", "opf:rootfile");
    if (rootfile == nullptr) {
        LOGE("%s failed parse container.xml, no rootfile element", __func__);
        return 0;
    }
    opf_path = xml_ext::getEleAttr(rootfile, "full-path");
    if (opf_path.empty()) {
        LOGE("%s failed content.opf path is null or empty", __func__);
        return 0;
    }

    LOGD("%s:content.opf path is [%s]", __func__, opf_path.c_str());
    std::string opf_content_data;
    if (1 != load_zip_entity_data(opf_path, opf_content_data)) {
        return 0;
    }
    doc.ClearError();
    doc.Clear();
    if (doc.Parse(opf_content_data.c_str(), opf_content_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse content.opf", __func__);
        return 0;
    }
    auto opfRoot = doc.RootElement();
    if (opfRoot == nullptr) {
        LOGE("%s opf root element is null", __func__);
        return 0;
    }

    auto opfMetadataEle =  xml_ext::firstChildEleWithTwoName(opfRoot, "metadata", "opf:metadata");
    if (opfMetadataEle == nullptr) {
        LOGE("%s opf metadata is null", __func__);
        return 0;
    }

    meta_info.title = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:title"));
    meta_info.author = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:creator"));
    meta_info.publisher = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:publisher"));
    meta_info.description = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:description"));
    meta_info.contributor = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:contributor"));
    meta_info.subject = xml_ext::getChildrenTexts(opfMetadataEle, "dc:contributor");
    meta_info.language = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:language"));
    meta_info.date = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:date"));
    meta_info.isbn = xml_ext::getText(xml_ext::getChildByNameAndAttr(opfMetadataEle, "dc:identifier", "opf:scheme", "ISBN"));

    std::string spine_toc_id = "";
    std::string spine_nav_id = "";
    auto spineElem = xml_ext::firstChildEleWithTwoName(opfRoot, "spine", "opf:spine");
    if (spineElem != nullptr) {
        std::string toc_id = xml_ext::getEleAttr(spineElem, "toc");
        if (!toc_id.empty()) {
            spine_toc_id = toc_id;
        }

        auto item = xml_ext::firstChildEleWithTwoName(spineElem, "itemref", "opf:itemref");
        while (item != nullptr) {
            std::string idref = xml_ext::getEleAttr(item, "idref");
            this->spines.emplace_back(BookSpine{idref});

            if (idref == epub_zfile_nav_xhtml) {
                spine_nav_id = idref;
            }

            item = xml_ext::nextSiblingEleWithTwoName(item, "itemref", "opf:itemref");
        }
    }

    auto manifestElem = xml_ext::firstChildEleWithTwoName(opfRoot, "manifest", "opf:manifest");
    if (manifestElem != nullptr) {
        auto item = xml_ext::firstChildEleWithTwoName(manifestElem, "item", "opf:item");
        while (item != nullptr) {
            std::string href = xml_ext::getEleAttr(item, "href");
            std::string id = xml_ext::getEleAttr(item, "id");
            std::string properties = xml_ext::getEleAttr(item, "properties");
            std::string media_type = xml_ext::getEleAttr(item, "media-type");
            this->manifests.emplace_back(BookManifest{href, id, media_type});

            if (media_type == xml_ext::MediaTypeTtf || media_type == xml_ext::MediaTypeOtf) {
                LOGD("%s:font assets:id[%s],href[%s],media_type[%s]", __func__, id.c_str(), href.c_str(), media_type.c_str());
            }

            if (!spine_toc_id.empty() && id == spine_toc_id && media_type == xml_ext::MediaTypeNcx) {
                ncx_path = href;
            }
            if ((properties == "nav" || (!spine_nav_id.empty() && id == spine_nav_id))
                    && media_type == xml_ext::MediaTypeHtml) {
                nav_path = href;
            }

            item = xml_ext::nextSiblingEleWithTwoName(item, "item", "opf:item");
        }
    }

    std::string zipNcxPath;
    for(std::string &item : this->zipEntities) {
        if (string_ext::endsWith(item, ".ncx")) {
            zipNcxPath = item;
            break;
        }
    }
    if (!zipNcxPath.empty() && ncx_path != zipNcxPath) {
        ncx_path = zipNcxPath;
    } else if (zipNcxPath.empty()) {
        ncx_path = "";
    }

    LOGI("%s:invoke done ncx_path[%s], nav_path[%s]", __func__, ncx_path.c_str(), nav_path.c_str());
    return 1;
}

/****
 * 从zip文件中获取封面
 * @param coverItemEle zip文件的Doc文档对应的封面的节点
 * @param book_title  书籍名称, 用于确定最终的本地保存的封面目录
 * @param zipfiles zip中的文件目录
 * @param uf 代表zip的文件指针
 * @return  封面的本地绝对路径
 */
std::string get_and_release_cover(const tinyxml2::XMLElement * coverItemEle, std::string &book_title, std::vector<std::string> &zipfiles, unzFile uf) {
    std::string cover_href = xml_ext::getEleAttr(coverItemEle, "href");
    std::string cover_type = xml_ext::getEleAttr(coverItemEle, "media-type");
    std::string ext = file_ext::get_media_type_ext(cover_type);

    cover_href = string_ext::base_url_decode(cover_href); //对路径进行URL解码, 防止出现无效路径

    if (!cover_href.empty() && !ext.empty()) {
        std::string output_cover_path = file_ext::get_cover_path(book_title, ext);
        if (!output_cover_path.empty()) {
            LOGD("%s: output cover path [%s]", __func__, output_cover_path.c_str());

            auto it = std::find_if(zipfiles.begin(), zipfiles.end(), [=](std::string &item){
                return item.find(cover_href) != std::string::npos;
            });
            if (it != zipfiles.end()) {
                cover_href = (*it);
            }
            LOGD("%s: cover zip href [%s]", __func__, cover_href.c_str());

            if (1 == zip_ext::write_zip_item_to_file(uf, cover_href, output_cover_path)) {
//                book_coverPath = output_cover_path;
                return output_cover_path;
            } else {
                LOGE("%s dump cover to local path failed", __func__);
            }
        } else {
            LOGE("%s: get cover path failed", __func__);
        }
    }
    return "";
}


int epub_util::load_epub(std::string fullpath,  //文件路径
                         std::string &book_coverPath,    //封面路径

                         std::string &book_title,
                         std::string &book_author,
                         std::string &book_contributor,

                         std::string &book_subject,
                         std::string &book_publisher,
                         std::string &book_date,

                         std::string &book_description,
                         std::string &book_review,
                         std::string &book_imprint,

                         std::string &book_copyright,
                         std::string &book_isbn,
                         std::string &book_asin,

                         std::string &book_language,
                         std::string &book_identifier,
                         bool &book_isEncrypted) {
    LOGI("%s:invoke", __func__);

    unzFile uf = unzOpen(fullpath.c_str());
    if (uf == nullptr) {
        LOGE("%s cannot open file[%s]", __func__, fullpath.c_str());
        return 0;
    }
    UnzGuard uf_guard{uf};

    unz_global_info gi; //获取zip文件中的条目数
    int err = unzGetGlobalInfo(uf, &gi);
    if (err != UNZ_OK) {
        LOGE("%s cannot get zip file info", __func__);
        return 0;
    }

    std::vector<std::string> zipfiles = zip_ext::inner_zip_files(uf);

    std::string container_data;
    if (1 != zip_ext::read_zip_file(uf, epub_zfile_container, container_data)) {
        return 0;
    }
    tinyxml2::XMLDocument doc;
    doc.ClearError();
    doc.Clear();
    if (doc.Parse(container_data.c_str(), container_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse container.xml", __func__);
        return 0;
    }
    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse container.xml, no root element", __func__);
        return 0;
    }

    auto rootfiles = xml_ext::firstChildEleWithTwoName(root, "rootfiles", "opf:rootfiles");
    if (rootfiles == nullptr) {
        LOGE("%s failed parse container.xml, no rootfiles element", __func__);
        return 0;
    }

    auto rootfile = xml_ext::firstChildEleWithTwoName(rootfiles, "rootfile", "opf:rootfile");
    if (rootfile == nullptr) {
        LOGE("%s failed parse container.xml, no rootfile element", __func__);
        return 0;
    }
    std::string content_path = xml_ext::getEleAttr(rootfile, "full-path");
    if (content_path.empty()) {
        LOGE("%s failed content.opf path is null or empty", __func__);
        return 0;
    }
    LOGD("%s:content.opf path is [%s]", __func__, content_path.c_str());
    std::string opf_content_data;
    if (1 != zip_ext::read_zip_file(uf, content_path, opf_content_data)) {
        return 0;
    }
    doc.ClearError();
    doc.Clear();
    if (doc.Parse(opf_content_data.c_str(), opf_content_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse content.opf", __func__);
        return 0;
    }
    auto opfRoot = doc.RootElement();
    if (opfRoot == nullptr) {
        LOGE("%s opf root element is null", __func__);
        return 0;
    }
    auto opfMetadataEle = xml_ext::firstChildEleWithTwoName(opfRoot, "metadata", "opf:metadata");
    if (opfMetadataEle == nullptr) {
        LOGE("%s opf metadata is null", __func__);
        return 0;
    }

    book_title = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:title"));
    book_author = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:creator"));
    book_publisher = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:publisher"));
    book_description = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:description"));
    book_contributor = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:contributor"));
    book_subject = xml_ext::getChildrenTexts(opfMetadataEle, "dc:contributor");
    book_language = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:language"));
    book_identifier = xml_ext::getChildrenTexts(opfMetadataEle, "dc:identifier");
    book_date = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:date"));
    book_isbn = xml_ext::getText(
            xml_ext::getChildByNameAndAttr(opfMetadataEle, "dc:identifier", "opf:scheme", "ISBN"));
    std::string cover_id = xml_ext::getEleAttr(xml_ext::getChildByNameAndAttr(opfMetadataEle, "meta", "name", "cover"), "content");

    auto manifestEle = xml_ext::firstChildEleWithTwoName(opfRoot, "manifest", "opf:manifest");

    if (!cover_id.empty()) {
        LOGD("%s: cover_id is %s", __func__, cover_id.c_str());
        auto coverItemEle = xml_ext::getChildByTwoNameAndAttr(manifestEle, "item", "opf:item", "id", cover_id);

        if (coverItemEle != nullptr) {
            book_coverPath = get_and_release_cover(coverItemEle, book_title, zipfiles, uf);
        }
    } else {
        //没有配置封面,则找到第一个图片资源作为封面
        auto firstImageItem = xml_ext::getChildByTwoNameAndAttr(manifestEle, "item", "opf:item", "media-type", xml_ext::MediaTypeJpg);

        if (firstImageItem == nullptr) {
            firstImageItem = xml_ext::getChildByTwoNameAndAttr(manifestEle, "item", "opf:item", "media-type", xml_ext::MediaTypePng);
        }
        if (firstImageItem == nullptr) {
            firstImageItem = xml_ext::getChildByTwoNameAndAttr(manifestEle, "item", "opf:item", "media-type", xml_ext::MediaTypeBmp);
        }
        if (firstImageItem != nullptr) {
            book_coverPath = get_and_release_cover(firstImageItem, book_title, zipfiles, uf);
        }
    }

    LOGI("%s:invoke done", __func__);
    return 1;
}

int epub_util::parseOpfData(std::vector<NavPoint> &points) {
    LOGI("%s:bookload:invoke", __func__);
    std::vector<std::pair<std::string, std::string>> orderedItemSrc;
    for(auto spine : spines) {
        auto it = std::find_if(manifests.begin(), manifests.end(), [=](BookManifest &item){
            return (spine.idref == item.id && item.media_type == xml_ext::MediaTypeHtml && !item.href.empty());
        });
        if (it != manifests.end()) {
            std::string href = (*it).href;
            href = string_ext::base_url_decode(href);
            orderedItemSrc.push_back(std::pair<std::string, std::string>(href, (*it).id));
        }
    }

    //指向相同位置的章节合并, 已经是拍好顺序了的,只需要前后比较即可
    std::vector<NavPoint> tmp;
    if (!points.empty()) {
        auto &firstPoint = points[0];
        tmp.push_back(firstPoint);
        for (int i = 1; i < points.size(); ++i) {
            auto &lastItem = tmp.back();
            auto &point = points[i];
            if (lastItem.src == point.src) {
                lastItem.text.append(" ").append(point.text);
            } else {
                tmp.push_back(point);
            }
        }
    }
    if (!tmp.empty() && tmp.size() != points.size()) {
        points.clear();
        points.insert(points.end(), tmp.begin(), tmp.end());
        int order = 1;
        for (auto &point: points) {
            point.playOrder = order++;
        }
    }

    //防止前面有遗漏章节
    int index = 0;
    int startOpfIndex = 0;
    std::vector<NavPoint> newPoints;
    if (!points.empty()) {
        while (index < points.size() && startOpfIndex < orderedItemSrc.size()) {
            auto &point = points[index];
            auto &opf = orderedItemSrc[startOpfIndex].first;

            if (point.src.find(opf) != std::string::npos) { //找到了
                if (startOpfIndex < orderedItemSrc.size() - 1) {
                    startOpfIndex++;
                }
            } else {
                //没有找到, 则在opf中往前找
                int opfIndex = startOpfIndex - 1;
                bool found = false;
                while (opfIndex >= 0) {
                    auto &prevOpf = orderedItemSrc[opfIndex].first;
                    if (point.src.find(prevOpf) != std::string::npos) { //在上一个找到了
                        found = true;
                        break;
                    } else {
                        opfIndex--;
                    }
                }

                if (!found) {    //往前找，没有找到了, 即表示 ncx是新的，opf也是新的， 则往后找opf,
                    opfIndex = startOpfIndex + 1;
                    found = false;
                    while (opfIndex < orderedItemSrc.size()) {
                        auto &nextOpf = orderedItemSrc[opfIndex].first;
                        if (point.src.find(nextOpf) != std::string::npos) { //在下一个找到了
                            found = true;
                            break;
                        } else {
                            opfIndex++;
                        }
                    }
                    if (found) {    //往后找，找到了，则将没有放入到ncx中的opf作为一个新的point，放入points中
                        for (int i = startOpfIndex; i < opfIndex; i++) {
                            NavPoint newpoint;
                            newpoint.src = orderedItemSrc[i].first;
                            newpoint.text = "";
                            newpoint.parentId = "";
                            newpoint.id = string_ext::generate_uuid();
                            newPoints.push_back(newpoint);
                        }
                        startOpfIndex = opfIndex + 1;
                    } else {    //往后找，也没有找到，则有问题
                        LOGE("%s:cannot match ncx and opf data", __func__);
                        return 0;
                    }
                } else {        //往前找，找到了,则继续遍历points
                    /* do nothing */
                }
            }
            newPoints.push_back(point);
            index++;
        }

        if (index >= points.size() && startOpfIndex < orderedItemSrc.size()) { //还有没有分配完的资源
            auto &lastPoint = points[points.size() - 1];
            int opfIndex = startOpfIndex;
            for (int i = opfIndex; i < orderedItemSrc.size(); i++) {
                auto &opf = orderedItemSrc[i].first;
                if (lastPoint.src.find(opf) != std::string::npos) {
                    continue;
                } else {
                    NavPoint point;
                    point.src = opf;
                    point.text = "";
                    point.id = string_ext::generate_uuid();
                    point.parentId = "";
                    newPoints.push_back(point);
                }
            }
        } else if (index < points.size() && startOpfIndex >= orderedItemSrc.size()) {
            for (int i = index; i < points.size(); i++) {
                newPoints.push_back(points[i]);
            }
        }
    }

    if (orderedItemSrc.size() == 1) {
        isSingleSrc = true;
    } else {
        isSingleSrc = false;
    }
    LOGI("%s:bookload:isSingleSrc=%d", __func__, isSingleSrc);

    //遍历的路径，如果某几个章节对应同一个资源，但是这些章节都不包含这个资源的开头部分
    if (!newPoints.empty()) {
        LOGI("%s:bookload:newPoints.size=%d", __func__, newPoints.size());
        for(int i = 0; i< newPoints.size(); ++i) {
            auto &point = newPoints[i];
            if (point.src.find("#") != std::string::npos) { //章节链接中有锚点
                //当前章节对应的资源和锚点
                std::string cur_src;
                std::string cur_anchor;
                std::vector<std::string> parts = string_ext::split(point.src, '#');
                if (parts.size() == 2) {
                    cur_src = parts[0];
                    cur_anchor = parts[1];
                }
                //上一个章节对应的资源和锚点
                std::string pre_src;
                std::string pre_anchor;
                if (i > 0) {
                    auto &pre_point = newPoints[i - 1];
                    if (pre_point.src.find("#") != std::string::npos) {
                        std::vector<std::string> pre_parts = string_ext::split(pre_point.src, '#');
                        if (pre_parts.size() == 2) {
                            pre_src = pre_parts[0];
                            pre_anchor = pre_parts[1];
                        }
                    } else {
                        pre_src = pre_point.src;
                    }
                }
                //当前章节对应的资源是否是一个新的资源
                bool new_src = false;
                if (pre_src != cur_src) {
                    new_src = true;
                }
                if (new_src) {
                    point.src = cur_src;
                }
            }
        }
    }

    int order = 1;
    if (!newPoints.empty()) {
        for (auto &point: newPoints) {
            point.playOrder = order++;
        }
    } else {
        if (orderedItemSrc.size() > 0) {
            for (int i = 0; i < orderedItemSrc.size(); ++i) {
                NavPoint point;
                point.src = orderedItemSrc[i].first;

                std::string item_name = orderedItemSrc[i].first;
                item_name = file_ext::extractFilename(item_name);
                if(!item_name.empty()) {
                    size_t lastSep = item_name.find_last_of(".");
                    item_name = (lastSep == std::string::npos) ? item_name : item_name.substr(0, lastSep);
                }
                point.text = item_name;

                point.id = orderedItemSrc[i].second;
                point.parentId = "";
                point.playOrder = i + 1;
                newPoints.push_back(point);
            }
        }
    }

    LOGI("%s:bookload:orderedItemSrc.size=%d", __func__, orderedItemSrc.size());

    // 检测超大单文件 spine 项，触发虚拟切分
    if (orderedItemSrc.size() <= 2 && newPoints.size() <= 2) {
        std::vector<NavPoint> merged;
        bool anySplit = false;
        for(auto &npoint : newPoints) {
            std::string spineSrc, anchorId;
            std::string npSrc = npoint.src;
            parseSrcName(npSrc, spineSrc, anchorId);
            // 封面/标题页不参与切分
            if (isLikelyCoverSrc(spineSrc)) {
                merged.push_back(npoint);
                continue;
            }
            // 加载原始 HTML
            std::string zipPath = cover_to_zip_entity(spineSrc);
            std::string rawHtml;
            auto _t_load = std::chrono::high_resolution_clock::now();
            if (1 != load_zip_entity_data(zipPath, rawHtml)) {
                merged.push_back(npoint);
                continue;
            }
            auto _t_load_end = std::chrono::high_resolution_clock::now();
            LOGI("perf:bookload: load_zip=%lldms",
                 std::chrono::duration_cast<std::chrono::milliseconds>(_t_load_end - _t_load).count());
            // 大小阈值判断（小于阈值不切）
            if (rawHtml.size() < SPLIT_SIZE_THRESHOLD) { //小于500K跳过， 不进行虚拟章节切分
                merged.push_back(npoint);
                continue;
            }
            // ── 直接对 raw HTML 提取 body + CSS。 ──
            std::string bodyContent = string_ext::extractBodyContent(rawHtml);   // raw body（纯字符串查找）
            std::vector<std::string> extHrefs;
            std::string cssText = string_ext::extractCssByString(rawHtml, extHrefs);  // cssText=内联，extHrefs=外链 href
            // cacheKey：format + bookId + zipPath（本地 bookId 已唯一，无需书指纹）
            std::string cacheKey = buildSplitCacheKey(FMT_EPUB, book_id, zipPath);
            // 调用基类通用切分（结果落盘 vsplit/<hash>.bin，内存不保留全文）
            auto _t_split = std::chrono::high_resolution_clock::now();
            std::vector<NavPoint> parts;
            LOGI("%s:bookload: then invoke detectAndSplit", __func__);
            if (1 == detectAndSplit(cacheKey, FMT_EPUB, book_id, bodyContent, cssText, extHrefs, parts)
                && parts.size() >= MIN_SPLIT_SEGMENTS) {
                // 切分成功：不再保留父点（type=0 父点 src 指向整文件，getWordCount/getChapter
                // 走原始全量路径会 OOM；段0 已承载父章名，父点是冗余且危险的）。
                if (!parts.empty()) {
                    parts[0].text = npoint.text; // 段0标题 = 父章名，保留可读性
                }
                for (auto &vp : parts) {
                    vp.src = spineSrc + "#vsplit_" + std::to_string(vp.splitSeq);
                    vp.parentId = npoint.id.empty() ? spineSrc : npoint.id;
                    vp.id = "vsplit_" + spineSrc + "_" + std::to_string(vp.splitSeq);
                    merged.push_back(vp);
                }
                anySplit = true;
            } else {
                // 切分失败，保留原点
                merged.push_back(npoint);
            }
            auto _t_split_end = std::chrono::high_resolution_clock::now();
            LOGI("perf:bookload: detectAndSplit=%lldms, parts=%zu",
                 std::chrono::duration_cast<std::chrono::milliseconds>(_t_split_end - _t_split).count(),
                 parts.size());
        }

        if (anySplit) { //有处理了虚拟章节的情况
            newPoints = merged;
        }

        // 这里对最终列表做唯一权威的连续重排。覆盖切分与未切分两条路径:未切分时与上方 788-792 的重排等价(幂等)。
        {
            int order = 1;
            for (auto &point : newPoints) {
                point.playOrder = order++;
            }
        }
    }

    LOGI("%s:bookload:invoke ready done, newPoints.size=%d", __func__, newPoints.size());

    points.clear();
    points.insert(points.end(), newPoints.begin(), newPoints.end());
    LOGI("%s:bookload:invoke done", __func__);
    return 1;
}

void epub_util::valid_points(/*in,out*/std::vector<NavPoint> &points) {
    LOGI("%s:invoke", __func__);
    if (points.empty()) {
        return;
    }
    // 边界检查：如果 zipEntities 为空，记录警告并返回
    if (zipEntities.empty()) {
        LOGW("%s: zipEntities is empty, cannot validate points", __func__);
        return;
    }

    std::vector<NavPoint> valid_points;
    int invalid_count = 0;

    // 遍历所有 NavPoint
    for (const auto &point : points) {
        // 检查 src 是否为空
        if (point.src.empty()) {
            LOGW("%s: NavPoint id=[%s], text=[%s] has empty src, skipping",
                 __func__, point.id.c_str(), point.text.c_str());
            invalid_count++;
            continue;
        }

        // 提取文件路径（分离锚点）
        std::string spine_src;
        std::string anchor_id;
        parseSrcName(const_cast<std::string&>(point.src), spine_src, anchor_id);

        // 检查提取的文件路径是否为空
        if (spine_src.empty()) {
            LOGW("%s: NavPoint id=[%s], text=[%s] has empty spine_src after parsing, skipping",
                 __func__, point.id.c_str(), point.text.c_str());
            invalid_count++;
            continue;
        }

        bool found = false; // 在 zipEntities 中查找文件

        // 方法1: 直接查找（精确匹配或包含匹配）
        auto it = std::find_if(zipEntities.begin(), zipEntities.end(),
                               [&spine_src](const std::string &entity) {
                                   // 精确匹配
                                   if (entity == spine_src) {
                                       return true;
                                   }
                                   // 包含匹配（entity 包含 spine_src）
                                   if (entity.find(spine_src) != std::string::npos) {
                                       return true;
                                   }
                                   return false;
                               });

        if (it != zipEntities.end()) {
            found = true;
            LOGD("%s: NavPoint id=[%s], text=[%s], src=[%s] found directly",
                 __func__, point.id.c_str(), point.text.c_str(), point.src.c_str());
        }
        // 如果找到了有效的文件，保留该 NavPoint
        if (found) {
            valid_points.push_back(point);
        } else {
            LOGW("%s: NavPoint id=[%s], text=[%s], src=[%s] NOT FOUND in zipEntities, skipping",
                 __func__, point.id.c_str(), point.text.c_str(), point.src.c_str());
            invalid_count++;
        }
    }
    // 如果有无效的 NavPoint 被移除，重新排序 playOrder
    if (invalid_count > 0) {
        LOGI("%s: Removed %d invalid NavPoints, %d valid points remaining",
             __func__, invalid_count, (int)valid_points.size());

        // 清空原数组并填充有效点
        points.clear();
        if(!valid_points.empty()) {  //还有一些有效的
            points.insert(points.end(), valid_points.begin(), valid_points.end());
        }

        // 重新设置 playOrder
        int order = 1;
        for (auto &p : points) {
            p.playOrder = order++;
        }
        LOGI("%s: Reordered playOrder for %d valid points", __func__, (int)points.size());
    } else {
        LOGI("%s: All %d NavPoints are valid", __func__, (int)points.size());
    }
    LOGI("%s:invoke done", __func__);
}


int epub_util::getChapters(/*out*/std::vector<NavPoint> &points) {
    LOGI("%s:bookload:invoke", __func__);
    auto start_time = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> lock(m_Mutex);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }

    if (!allChapters.empty()) {
        points.insert(points.end(), allChapters.begin(), allChapters.end());
        LOGI("%s:invoke done", __func__);
        return 1;
    }

    //toc.ncx 文件不是必须的。根据 EPUB 3 的规范，toc.ncx 文件已被 nav.xhtml 所取代，因此它不再是 EPUB 的强制要求。
    // 然而，许多出版商为了向前兼容 EPUB 2 的阅读器，仍然会保留 toc.ncx 文件
    std::string ncx_data;
    std::string nav_data;
    if (!nav_path.empty()) {
        LOGI("%s:bookload:nav_path=%s", __func__, nav_path.c_str());
        nav_path = cover_to_zip_entity(nav_path);
        if (1 != load_zip_entity_data(nav_path, nav_data)) {
            LOGE("%s failed get1 [%s] nav data failed", __func__, nav_path.c_str());
            return 0;
        }
//        LOGD("%s failed get0 [%s] nav data failed", __func__ , nav_path.c_str());

        if (!nav_data.empty()) {
            LOGI("%s:bookload:nav_data=%s", __func__, nav_data.c_str());
            if (1 != xml_ext::parseEpub3NcxData(nav_data, points, nav_path)) {
                LOGE("%s failed, cannot pass ncx", __func__);
                return 0;
            }
        }
    } else if (!ncx_path.empty()) {
        LOGI("%s:bookload:ncx_path=%s", __func__, ncx_path.c_str());
        ncx_path = cover_to_zip_entity(ncx_path);
        if (1 != load_zip_entity_data(ncx_path, ncx_data)) {
            LOGE("%s failed get0 [%s] ncx data failed", __func__, ncx_path.c_str());
            return 0;
        }
        LOGD("%s ncx_path[%s]", __func__, ncx_path.c_str());

        if (!ncx_data.empty()) {
            LOGI("%s:bookload:ncx_data=%s", __func__, ncx_data.c_str());
            int ret = xml2_ext::normalize_xml(ncx_data);
            if (1 != xml_ext::parseNcxData(ncx_data, points)) {
                LOGE("%s failed, cannot pass ncx", __func__);
                return 0;
            }
        }
        LOGI("%s:bookload:then start valid_points", __func__);
        valid_points(points);
        LOGI("%s:bookload:then valid_points done", __func__);
    }
    if (1 != parseOpfData(points)) {
        LOGE("%s failed, cannot pass opf", __func__);
        return 0;
    }

    LOGI("%s:bookload:then parseOpfData done", __func__);

    allChapters.clear();
    allChapters.insert(allChapters.end(), points.begin(), points.end());

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:bookload:invoke done duration = %lld ms", __func__, duration);
    return 1;
}

int epub_util::getChapter(JNIEnv *env, long book_id,
                          const char *path,
                          NavPoint &chapter,
                          std::vector<DocText> &docTexts) {
    LOGI("%s:invoke", __func__);
    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    auto start_time = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> lock(m_Mutex2);
    LOGD("%s invoke,playOrder[%d],src[%s]", __func__, chapter.playOrder, chapter.src.c_str());
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }
    if (app_ext::appFileDir.empty()) {
        LOGE("%s:failed, appFileDir is empty so pass", __func__);
        return 0;
    }

    std::string spineSrc;
    std::string anchorId;
    parseSrcName(chapter.src, spineSrc, anchorId);
    LOGD("%s chapter spineSrc[%s] anchorId=[%s]", __func__, spineSrc.c_str(), anchorId.c_str());

    // === 虚拟切分章节：从文件缓存按段读取小正文，交给解析器 ===
    if (chapter.type == 1) {
        // cacheKey: format + bookId + zipPath（本地 bookId 唯一，无需书指纹）
        std::string zipPath = cover_to_zip_entity(spineSrc);
        std::string cacheKey = buildSplitCacheKey(FMT_EPUB, book_id, zipPath);

        // 读一次索引：同时拿到段表(含 fileOffset) + cssText + 外链 href 列表
        std::vector<SplitSegment> segs;
        std::string cssText;
        std::vector<std::string> extCssHrefs;  // 外链 <link> href（v9，渲染期从 manifest 加载）
        if (1 != loadSplitIndex(cacheKey, FMT_EPUB, segs, cssText, extCssHrefs)) {
            // 文件缓存不存在（冷启动被清理/首次）：按需生成该 zipPath 的缓存后重试
            LOGW("%s: cache miss (cacheKey=%s, splitSeq=%d), generating...",
                 __func__, cacheKey.c_str(), chapter.splitSeq);
            if (1 != ensureSplitOnDisk(zipPath) ||
                1 != loadSplitIndex(cacheKey, FMT_EPUB, segs, cssText, extCssHrefs)) {
                LOGE("%s: loadSplitIndex failed after ensure for splitSeq=%d",
                     __func__, chapter.splitSeq);
                return 0;
            }
        }
        // 校验 splitSeq 合法
        if (chapter.splitSeq < 0 || chapter.splitSeq >= (int)segs.size()) {
            LOGE("%s: splitSeq=%d out of range [0,%d)", __func__,
                 chapter.splitSeq, (int)segs.size());
            return 0;
        }
        const SplitSegment &seg = segs[chapter.splitSeq];
        if (seg.length == 0 || seg.length > MAX_SPLIT_HTML_SIZE) {
            LOGE("%s: seg.length=%zu invalid", __func__, seg.length);
            return 0;
        }
        // 按段 seek 直读该段正文（内存占用 = 该段，通常几十~几百 KB）
        std::string bodyContent;
        try {
            std::string path = getSplitCachePath(cacheKey);
            std::ifstream ifs(path, std::ios::binary);
            if (!ifs.is_open()) {
                LOGE("%s: open cache file failed: %s", __func__, path.c_str());
                return 0;
            }
            ifs.seekg((std::streamoff)seg.fileOffset, std::ios::beg);
            bodyContent.resize(seg.length);
            ifs.read(&bodyContent[0], seg.length);
            if (ifs.fail()) {
                LOGE("%s: read seg body failed, splitSeq=%d", __func__, chapter.splitSeq);
                return 0;
            }
        } catch (const std::exception &e) {
            LOGE("%s: exception %s", __func__, e.what());
            return 0;
        }
        if (1 == renderSplitSegment(env, bodyContent, cssText, extCssHrefs, spineSrc, docTexts)) {
            auto end_time = std::chrono::high_resolution_clock::now();
            auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
            LOGD("%s: splitSeq=%d done, docTexts=%zu, dur=%lldms",
                 __func__, chapter.splitSeq, docTexts.size(), duration);
            return 1;
        }
        LOGE("%s: renderSplitSegment failed for splitSeq=%d", __func__, chapter.splitSeq);
        return 0;
    }

    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    std::string endAnchorId;
    std::vector<NavPoint> points;
    if (1 == getChapters(points)) {
        int targetIndex = -1;
        for (int i = 0; i < points.size(); ++i) {
            if (points[i].src == chapter.src) {
                targetIndex = i + 1;
                break;
            }
        }
        if (targetIndex >= 0 && targetIndex < points.size()) {
            auto &nextChapter = points[targetIndex];
            std::string nextChapterSpineSrc;
            std::string nextChapterAnchorId;
            parseSrcName(nextChapter.src, nextChapterSpineSrc, nextChapterAnchorId);
            LOGD("%s nextChapter spine_src[%s], anchorId=[%s]", __func__, nextChapterSpineSrc.c_str(), nextChapterAnchorId.c_str());
            if (nextChapterSpineSrc == spineSrc && !nextChapterAnchorId.empty()) {
                endAnchorId = nextChapterAnchorId;
            }
        }
    }

    LOGD("%s invoke next run_flag=%d, currentSrc=[%s]", __func__, run_flag, currentSrc.c_str());
    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    if (spineSrc != currentSrc) {
        spineSrc = cover_to_zip_entity(spineSrc);
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }
    }

    if (spineSrc != currentSrc) {
        cssInfos.clear();
        std::string chapter_data;
        std::string page_css_style;
        LOGD("%s::transform to zip entity src is %s", __func__, spineSrc.c_str());
        if (1 != load_zip_entity_data(spineSrc, chapter_data)) {
            LOGE("%s read [%s] failed", __func__, spineSrc.c_str());
            return 0;
        }
        LOGD("%s::transform done ,chapter_data.size = %zu", __func__, chapter_data.size());
        if (1 != tidyh5_ext::tidy_html_with_css(chapter_data, page_css_style)) {
            LOGE("%s tidy html %s failed", __func__, spineSrc.c_str());
            return 0;
        }
        LOGD("%s::tidy_html done ,chapter_data.size = %zu", __func__, chapter_data.size());
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }

        string_ext::removeHtmlTagWrap(page_css_style, "style");
        if (!page_css_style.empty()) {
            css_ext::parse_css(page_css_style, cssInfos);
        }

        doc.ClearError();
        doc.Clear();
        LOGD("%s::doc clear try to parse", __func__);
        if (doc.Parse(chapter_data.c_str(), chapter_data.size()) != tinyxml2::XML_SUCCESS) {
            LOGE("%s failed to parse %s", __func__, spineSrc.c_str());
            return 0;
        }

        // parse external CSS: extract <link> href, match in cssSrc, load matched CSS files
        parse_css_list();
        if (!cssSrc.empty()) {
            tinyxml2::XMLElement *extRoot = doc.RootElement();
            if (extRoot) {
                tinyxml2::XMLElement *headEle = extRoot->FirstChildElement("head");
                if (headEle) {
                    std::vector<std::string> loadedCssPaths;
                    tinyxml2::XMLElement *linkEle = headEle->FirstChildElement("link");
                    while (linkEle) {
                        std::string rel = xml_ext::getEleAttr(linkEle, "rel");
                        if (rel == "stylesheet") {
                            std::string href = xml_ext::getEleAttr(linkEle, "href");
                            if (!href.empty()) {
                                href = string_ext::base_url_decode(href);
                                std::string cssFilename = file_ext::extractFilename(href);
                                if (!cssFilename.empty()) {
                                    for (auto &cssHref : cssSrc) {
                                        if (cssHref.find(cssFilename) != std::string::npos) {
                                            if (!run_flag) { return 0; }
                                            std::string cssZipPath = cover_to_zip_entity(cssHref);
                                            if (cssZipPath.empty()) {
                                                LOGE("%s: cover_to_zip_entity failed for cssHref[%s]", __func__, cssHref.c_str());
                                                break;
                                            }
                                            bool alreadyLoaded = false;
                                            for (auto &loaded : loadedCssPaths) {
                                                if (loaded == cssZipPath) {
                                                    alreadyLoaded = true;
                                                    break;
                                                }
                                            }
                                            if (alreadyLoaded) { break; }
                                            std::string cssData;
                                            if (1 == load_zip_entity_data(cssZipPath, cssData) && !cssData.empty()) {
                                                css_ext::parse_css(cssData, cssInfos);
                                                loadedCssPaths.push_back(cssZipPath);
                                                LOGD("%s: loaded external css [%s]", __func__, cssZipPath.c_str());
                                            } else {
                                                LOGE("%s: load external css [%s] failed", __func__, cssZipPath.c_str());
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        linkEle = linkEle->NextSiblingElement("link");
                    }
                }
            }
        }

        currentSrc = spineSrc;
    }

    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    int flagAdd = 0;
    tinyxml2::XMLElement *childEle = xml_ext::getStartElement(doc.RootElement(), &flagAdd, anchorId);
    LOGD("%s::getStartElement done flagAdd=%d, anchorId = %s", __func__, flagAdd, anchorId.c_str());

    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    if (childEle != nullptr) {
        std::vector<TagInfo> tags;
        xml_ext::parse(childEle, docTexts, anchorId, endAnchorId, &flagAdd, spineSrc);
        LOGD("%s::parse done, docTexts.size = %zu", __func__, docTexts.size());

        if (docTexts.empty() && anchorId.empty()) { //防止页面为空页面的补救措施
            auto eleRoot = doc.RootElement();
            if (eleRoot) {
                auto eleHead = eleRoot->FirstChildElement("head");
                if (eleHead) {
                    auto eleTitle = eleHead->FirstChildElement("title");
                    std::string title = xml_ext::getText(eleTitle);
                    if (!title.empty()) {
                        std::vector<TagInfo> tags;
                        tags.push_back(TagInfo{"", "", "h1", 0, title.size(), "", ""});
                        docTexts.push_back(DocText{title, tags});
                    }
                }
            }
        }

        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }
//        mockFirstPage(chapter, docTexts, meta_info.title, meta_info.author, meta_info.publisher);
        handle_tags(env, docTexts, cssInfos);
    } else {
        LOGE("%s: invoke failed, childEle is null", __func__);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: invoke done duration = %lld ms", __func__, duration);
    return 1;
}

void epub_util::handle_tags(JNIEnv *env, std::vector<DocText> &docTexts, std::vector<CssInfo> &cssInfos) {
    auto start_time = std::chrono::high_resolution_clock::now();
    LOGI("%s:invoke", __func__);
    // v4.0 新增:对累积后的 cssInfos 做 sort + deduplicate
    // 修复 specificity bug(weight dead field)和重复注入问题
    // 调用时机:parse_css 已累积完内联 <style> + 外链 <link> CSS 后
    css_ext::sort_and_deduplicate_inplace(cssInfos);
    for (auto &doctext: docTexts) {
        if (!doctext.tagInfos.empty()) {
            auto itag = doctext.tagInfos.begin();
            for(; itag != doctext.tagInfos.end(); ++itag) {  //图片Tag, 需要解析其宽高, 以及其本地路径
                if ((*itag).name == "img" || (*itag).name == "image") {
                    TagInfo &imgtag = (*itag);
                    std::string params = imgtag.params;
                    auto kvs = xml_ext::parse_str_params(params);
                    std::string imgSrc;
                    int width = 0;
                    int height = 0;
                    for(auto &kv : kvs) {
                        if (kv.first == "src") {
                            imgSrc = kv.second;
                        } else if (kv.first == "width") {
                            width = string_ext::toInt(kv.second);
                        } else if (kv.first == "height") {
                            height = string_ext::toInt(kv.second);
                        }
                    }
                    if (!imgSrc.empty()) {
                        int srcWidth = 0;
                        int srcHeight = 0;
                        if (1 == cache_image(env, imgSrc, &srcWidth, &srcHeight)) {
                            std::string imgPath = file_ext::get_img_path(book_id, imgSrc);
                            if (srcWidth > 0 && srcHeight > 0 && !imgPath.empty()) {
                                std::stringstream ss;
                                int w = width, h = height;
                                if (srcWidth > width || srcHeight > height) {
                                    w = srcWidth;
                                    h = srcHeight;
                                }
                                ss <<  "src=" + imgPath + "&width=" + std::to_string(w) + "&height=" + std::to_string(h);
                                for(auto &kv : kvs) {
                                    if (kv.first != "src" && kv.first != "width" && kv.first != "height") {
                                        ss << "&" << kv.first << "=" << kv.second;
                                    }
                                }
                                imgtag.params = ss.str();
                            }
                        }
                    }
                } else {
                    if (!cssInfos.empty()) {
                        TagInfo &item_tag = (*itag);
                        std::string params = item_tag.params;
                        std::vector<RuleData> rule_datas;

                        std::vector<std::string> css_classes;
                        std::string tag_name = item_tag.name;
                        std::string anchor_id = item_tag.anchor_id;

                        if (!params.empty()) {
                            auto kvs = xml_ext::parse_str_params(params);
                            for (auto &kv : kvs) {
                                if (kv.first == "class" && !kv.second.empty()) {
                                    std::istringstream iss(kv.second);
                                    std::string cls;
                                    while (iss >> cls) {
                                        if (!cls.empty()) {
                                            css_classes.push_back(cls);
                                        }
                                    }
                                    break;
                                }
                            }
                        }

                        for (auto &info : cssInfos) {
                            bool match = false;
                            if (info.type == "class" && !css_classes.empty()) {
                                for (auto &cls : css_classes) {
                                    if (info.identifier == cls) {
                                        match = true;
                                        break;
                                    }
                                }
                            }
                            if (!match && info.type == "tag" && info.identifier == tag_name) {
                                match = true;
                            }
                            if (!match && info.type == "id" && !anchor_id.empty() && info.identifier == anchor_id) {
                                match = true;
                            }
                            if (match) {
                                rule_datas.insert(rule_datas.end(), info.ruleDatas.begin(), info.ruleDatas.end());
                            }
                        }

                        if (!rule_datas.empty()) {
                            // v4.0:调共享函数 apply_css_to_params
                            // - 前置条件:cssInfos 已在 handle_tags 入口按 weight 升序排序(高 specificity 在后)
                            // - 该函数做 last-wins 合并同名属性,让 params 字符串无重复 key
                            // - v4.0:不再显式 continue background(native 透传所有 CSS 属性)
                            std::string result = css_ext::apply_css_to_params(params, rule_datas);
                            if (result != params) {
                                item_tag.params = result;
                            }
                        }
                    }
                }
            }
        }
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    //    //输出结果统计信息(性能分析)
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:invoke done duration = %lld ms", __func__, duration);
}

int epub_util::parse_css_list() {
    LOGI("%s:invoke", __func__);
    if (isEmptyCss) {
        LOGI("%s:invoke isEmptyCss=true, done", __func__);
        return 0;
    }
    if (cssSrc.empty()) {
        for (const auto &manifest: manifests) {
            if (manifest.media_type == xml_ext::MediaTypeCss) {
                cssSrc.push_back(manifest.href);
            }
        }
    }
    if (cssSrc.empty()) {
        isEmptyCss = true;
    }
    LOGI("%s:invoke done, cssSrc.size=%d", __func__, cssSrc.size());
    return 1;
}


int32_t epub_util::getWordCount(std::vector<ChapterCount> &wordCounts) {
    LOGI("%s:invoke", __func__);
    auto start_time = std::chrono::high_resolution_clock::now();
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }
    std::vector<NavPoint> chapters;
    if (getChapters(chapters) != 1) {
        return 0;
    }
    if (chapters.empty()) {
        return 0;
    }

    struct ChapterSrcInfo {
        std::string spineSrc;
        std::string anchorId;
        int playOrder;
    };
    std::vector<ChapterSrcInfo> srcInfos; // 仅真实章（type==0）走原 group 逻辑
    size_t total = 0;

    // 先处理虚拟切分章（type==1）：优先从缓存 segment 的预统计字数读取（切分时已算好），
    // 避免对每个虚拟章二次 stripTagsAndCountBody（消除 getWordCount 的全量 tidy/strip 开销）。
    for (auto &ch : chapters) {
        if (ch.type != 1) {
            // 真实章，加入 srcInfos 走原逻辑
            ChapterSrcInfo info;
            parseSrcName(ch.src, info.spineSrc, info.anchorId);
            info.playOrder = ch.playOrder;
            srcInfos.push_back(info);
            continue;
        }
        // 虚拟章：从文件缓存索引读段字数/图片数（切分时一次性统计，无需读正文）
        std::string vSpineSrc, vAnchorId;
        parseSrcName(ch.src, vSpineSrc, vAnchorId);
        std::string zipPath = cover_to_zip_entity(vSpineSrc);
        std::string cacheKey = buildSplitCacheKey(FMT_EPUB, book_id, zipPath);
        size_t charCount = 0;
        size_t picCount = 0;
        if (1 != loadSegmentMeta(cacheKey, FMT_EPUB, ch.splitSeq, charCount, picCount)) {
            // 文件缓存不存在（冷启动被清理）：按需生成后重试
            // 若重试仍失败（文件>50MB / 切分无切分点），跳过该虚拟章，
            // 不写入 charCount=0 的错误统计值（否则上层会把全书字数算成 0 写入 DB）
            if (1 != ensureSplitOnDisk(zipPath) ||
                1 != loadSegmentMeta(cacheKey, FMT_EPUB, ch.splitSeq, charCount, picCount)) {
                LOGE("%s: virtual chapter playOrder=%d splitSeq=%d wordCount unavailable, skipping",
                     __func__, ch.playOrder, ch.splitSeq);
                continue;
            }
        }
        // ── P5: 字数延迟统计。切分时 charCount/picCount 默认 0（未统计），
        //   loadSegmentMeta 成功返回 0 是正常态。此处必须现算，否则全书字数=0 写入 DB。
        //   首次现算后经 updateChapterWordCountUseCase 写 DB，后续读 DB 不再进入此分支。 ──
        if (charCount == 0 && picCount == 0) {
            std::string segBody;
            if (1 == loadSegmentBody(cacheKey, FMT_EPUB, ch.splitSeq, segBody) && !segBody.empty()) {
                charCount = string_ext::stripTagsAndCountBody(segBody);
                picCount = string_ext::countImages(segBody);
            }
        }
        wordCounts.emplace_back(ChapterCount{ch.playOrder, charCount, picCount});
        total += charCount + picCount;
    }

    // 真实章：原有 group + count_text_pic_batch 逻辑
    if (srcInfos.empty()) {
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
        LOGD("%s: duration = %lld ms, total = %zu", __func__, duration, total);
        return total;
    }

    struct Group {
        std::string spineSrc;
        std::vector<std::string> anchors;
        std::vector<int> orders;
    };
    std::vector<Group> groups;
    for (auto &info : srcInfos) {
        if (groups.empty() || groups.back().spineSrc != info.spineSrc) {
            groups.push_back({info.spineSrc, {}, {}});
        }
        groups.back().anchors.push_back(info.anchorId);
        groups.back().orders.push_back(info.playOrder);
    }

    size_t realTotal = 0;
    std::string rawHtml;
    for (auto &group : groups) {
        std::string zipPath = cover_to_zip_entity(group.spineSrc);
        if (1 != load_zip_entity_data(zipPath, rawHtml)) {
            LOGE("%s: load_zip_entity_data failed for [%s]", __func__, zipPath.c_str());
            return 0;
        }
        std::vector<std::pair<size_t, size_t>> counts;
        string_ext::count_text_pic_batch(rawHtml, group.anchors, counts);
        for (size_t j = 0; j < counts.size(); j++) {
            wordCounts.emplace_back(ChapterCount{group.orders[j], counts[j].first, counts[j].second});
            realTotal += counts[j].first + counts[j].second;
            LOGD("%s: playOrder[%d], anchor=[%s], charCount[%zu], picCount[%zu]",
                 __func__, group.orders[j], group.anchors[j].c_str(),
                 counts[j].first, counts[j].second);
        }
    }
    total += realTotal;

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: duration = %lld ms, total = %zu", __func__, duration, total);
    return total;
}

/***
 * 缓存图片
 * @param env
 * @param imgSrc
 * @param width
 * @param height
 * @return
 */
int epub_util::cache_image(JNIEnv *env,
                           std::string &imgSrc,
                           int *width,
                           int *height) {
    //文件路径
    std::string fullpath = file_ext::get_img_path(book_id, imgSrc);
    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }
    int ret = file_ext::checkAndCreateDir(file_ext::get_img_parent_path(book_id), imgSrc);
    if (ret < 0) {
        LOGE("%s:failed, creat dir err", __func__);
        return 0;
    }

    if (ret == 0) {//缓存文件不存在，缓存路径存在或者创建缓存路径成功
        std::string imgzip = imgSrc;
        for (auto &manifest: manifests) {
            if ((manifest.media_type == xml_ext::MediaTypeBmp ||
                 manifest.media_type == xml_ext::MediaTypePng ||
                 manifest.media_type == xml_ext::MediaTypeGif ||
                 manifest.media_type == xml_ext::MediaTypeJpg) &&
                (imgSrc == manifest.href || imgSrc.find(manifest.href) != std::string::npos)) {
                imgzip = manifest.href;
                break;
            }
        }
        imgzip = cover_to_zip_entity(imgzip);

        if (imgzip.empty()) {
            LOGE("%s cannot find [%s] in manifest", __func__, imgSrc.c_str());
            return 0;
        }
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }
        if (1 != write_zip_entity_to_file(imgzip, fullpath)) {
            LOGE("%s write image[%s] to path[%s] failed", __func__, imgzip.c_str(), fullpath.c_str());
            return 0;
        }
    }

    //缓存文件已经存在
    bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
    return 1;
}
