//
// Created by wxn on 2026-05-27.
//

#include "html_util.h"
#include <chrono>
#include <sstream>
#include <fstream>
#include <filesystem>
#include "../base64/decode.h"

namespace fs = std::filesystem;

static int loadHtmlMetaInfo(tinyxml2::XMLDocument &doc, MetaInfo &meta_info) {
    tinyxml2::XMLElement *root = doc.RootElement();
    if (root == nullptr) {
        return -1;
    }

    auto head = root->FirstChildElement("head");
    if (head != nullptr) {
        auto titleEle = head->FirstChildElement("title");
        if (titleEle != nullptr) {
            meta_info.title = xml_ext::getEleText(titleEle);
        }
    }

    auto body = root->FirstChildElement("body");
    if (body != nullptr) {
        auto firstH1 = body->FirstChildElement("h1");
        if (firstH1 != nullptr && meta_info.title.empty()) {
            meta_info.title = xml_ext::getEleText(firstH1);
        }
    }

    auto metaAuthor = head != nullptr ? head->FirstChildElement("meta") : nullptr;
    while (metaAuthor != nullptr) {
        std::string name = xml_ext::getEleAttr(metaAuthor, "name");
        if (name == "author") {
            meta_info.author = xml_ext::getEleAttr(metaAuthor, "content");
            break;
        }
        metaAuthor = metaAuthor->NextSiblingElement("meta");
    }

    return 1;
}

int html_util::html_init() {
    LOGI("%s:invoke", __func__);
    if (book_id == 0L || book_path.empty()) {
        LOGE("%s:book_id or book_path is empty", __func__);
        return 0;
    }

    std::string file_data;
    int dataSize = file_ext::readStringFromFile(book_path, file_data);
    if (dataSize <= 0) {
        LOGE("%s:failed to read file: %s", __func__, book_path.c_str());
        return 0;
    }

    if (1 != tidyh5_ext::tidy_html(file_data)) {
        LOGE("%s:tidy html failed", __func__);
        return 0;
    }

    doc.ClearError();
    doc.Clear();
    if (doc.Parse(file_data.c_str(), file_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s:failed to parse tidied html", __func__);
        return 0;
    }

    loadHtmlMetaInfo(doc, meta_info);
    LOGI("%s:invoke done, title=[%s]", __func__, meta_info.title.c_str());
    return 1;
}


void html_util::html_release() {

    LOGI("%s:invoke", __func__);
    std::lock_guard<std::mutex> lock(m_Mutex);
    if (initStatus) {
        doc.Clear();
        doc.ClearError();
        initStatus = false;
    }
    LOGI("%s:invoke done", __func__);
}

/***
  * 缓存图片
  * HTML 文件中 <img> 的 src 有三种情况：
  * 1. 网络链接 (http:// https://) → 不缓存，返回 0，保留原始 URL 由渲染层处理
  * 2. Base64 data URI (data:image/...;base64,...) → 解码并保存到缓存目录，imgSrc 替换为缓存文件名
  * 3. 本地相对/绝对路径 → 复制到缓存目录（少见场景），imgSrc 不变
  * @param env
  * @param imgSrc [in/out] 图片的src属性值。网络链接不变；base64 替换为缓存文件名；本地路径不变
  * @param width [out] 图片宽度
  * @param height [out] 图片高度
  * @return 1 成功（已缓存），0 跳过（网络链接或失败）
  */
int html_util::cache_image(JNIEnv *env,
                std::string &imgSrc,
                int *width,
                int *height) {

    std::string src = imgSrc;
    if (src.empty()) {
        LOGE("%s failed, src is empty", __func__ );
        return 0;
    }

    string_ext::trim(src);

    // 网络链接：不缓存，保留原始 URL 由上层渲染
    if (string_ext::startWith(src, "http://") || string_ext::startWith(src, "https://")) {
        LOGD("%s skip network url: %s", __func__, src.c_str());
        return 0;
    }

    // Base64 data URI: data:image/png;base64,iVBORw0KGgo...
    if (string_ext::startWith(src, "data:")) {
        size_t commaPos = src.find(',');
        if (commaPos == std::string::npos) {
            LOGE("%s invalid data URI, no comma: %s", __func__, src.substr(0, 50).c_str());
            return 0;
        }

        std::string header = src.substr(5, commaPos - 5); // "image/png;base64"
        std::string base64Data = src.substr(commaPos + 1);

        // 提取图片格式
        std::string imgtype;
        size_t semiPos = header.find(';');
        if (semiPos != std::string::npos) {
            std::string mimeType = header.substr(0, semiPos);
            imgtype = file_ext::get_media_type_ext(mimeType);
        }
        if (imgtype.empty()) {
            imgtype = "png";
        }

        // 生成唯一文件名
        std::string cacheName = "htmlimg_" + string_ext::generate_uuid() + "." + imgtype;
        std::string fullpath = file_ext::get_img_path(book_id, cacheName);
        if (!run_flag) {
            return 0;
        }

        int ret = file_ext::checkAndCreateDir(file_ext::get_img_parent_path(book_id), cacheName);
        if (ret < 0) {
            LOGE("%s:failed, creat dir err", __func__);
            return 0;
        } else if (ret == 0) {
            string_ext::trim(base64Data);
            base64Data = string_ext::cleanStr(base64Data);
            if (base64Data.length() > 1) {
                std::istringstream isstream(base64Data);
                std::ofstream imgFileStream(fullpath, std::ios::binary);
                base64::decoder b64decoder;
                b64decoder.decode(isstream, imgFileStream);
            }
        }

        bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
        imgSrc = cacheName;
        return 1;
    }

    // 本地文件路径（少见）
    std::string srcDecoded = string_ext::base_url_decode(src);

    fs::path htmlDir = fs::path(book_path).parent_path();
    std::string imgAbsolutePath;
    if (fs::path(srcDecoded).is_absolute()) {
        imgAbsolutePath = srcDecoded;
    } else {
        imgAbsolutePath = (htmlDir / srcDecoded).string();
    }

    std::error_code ec;
    fs::path resolvedPath = fs::canonical(imgAbsolutePath, ec);
    if (ec) {
        LOGE("%s image file not found: %s", __func__, imgAbsolutePath.c_str());
        return 0;
    }
    imgAbsolutePath = resolvedPath.string();

    fs::path resolvedHtmlDir = fs::canonical(htmlDir, ec);
    if (!ec && imgAbsolutePath.find(resolvedHtmlDir.string()) != 0) {
        LOGE("%s path traversal detected: %s", __func__, imgAbsolutePath.c_str());
        return 0;
    }

    std::string fullpath = file_ext::get_img_path(book_id, imgSrc);
    if (!run_flag) {
        return 0;
    }

    int ret = file_ext::checkAndCreateDir(file_ext::get_img_parent_path(book_id), imgSrc);
    if (ret < 0) {
        LOGE("%s:failed, creat dir err", __func__);
        return 0;
    } else if (ret == 0) {
        std::ifstream srcFile(imgAbsolutePath, std::ios::binary);
        if (!srcFile.is_open()) {
            LOGE("%s:failed to open source image: %s", __func__, imgAbsolutePath.c_str());
            return 0;
        }
        std::ofstream dstFile(fullpath, std::ios::binary);
        if (!dstFile.is_open()) {
            LOGE("%s:failed to create cache file: %s", __func__, fullpath.c_str());
            return 0;
        }
        dstFile << srcFile.rdbuf();
        srcFile.close();
        dstFile.close();
    }

    bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
    return 1;
}

int html_util::getChapters(/*out*/std::vector<NavPoint> &points) {
    LOGI("%s:invoke", __func__);
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

    NavPoint chapter;
    chapter.text = meta_info.title.empty() ? file_ext::extractFilename(book_path) : meta_info.title;
    chapter.src = book_path;
    chapter.id = string_ext::generate_uuid();
    chapter.playOrder = 1;
    allChapters.emplace_back(chapter);

    points.insert(points.end(), allChapters.begin(), allChapters.end());

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:invoke done duration = %lld ms", __func__, duration);
    return 1;
}

int html_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter,
               std::vector<DocText> &docTexts)  {
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

    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    std::vector<CssInfo> cssInfos;
    std::string chapter_data;
    std::string page_css_style;

    std::string filePath = (path != nullptr && strlen(path) > 0) ? path : book_path;
    int dataSize = file_ext::readStringFromFile(filePath, chapter_data);
    if (dataSize <= 0) {
        LOGE("%s failed to read file: %s", __func__, filePath.c_str());
        return 0;
    }

    LOGD("%s::transform done ,chapter_data.size = %zu", __func__, chapter_data.size());
    if (1 != tidyh5_ext::tidy_html_with_css(chapter_data, page_css_style)) {
        LOGE("%s tidy html failed", __func__);
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

    chapterDoc.ClearError();
    chapterDoc.Clear();
    LOGD("%s::doc clear try to parse", __func__);
    if (chapterDoc.Parse(chapter_data.c_str(), chapter_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse", __func__);
        return 0;
    }


    tinyxml2::XMLElement *extRoot = chapterDoc.RootElement();
    if (extRoot == nullptr) {
        LOGE("%s failed parse html, no root element", __func__);
        return 0;
    }

    tinyxml2::XMLElement *bodyEle = extRoot->FirstChildElement("body");
    if (bodyEle == nullptr) {
        LOGE("%s failed parse html, no body element", __func__);
        return 0;
    }

    tinyxml2::XMLElement *mainEle =  xml_ext::findEleByTag(extRoot, "main");
    if (mainEle == nullptr) {
        LOGW("%s failed parse html, no main element so use body element", __func__);
        mainEle = extRoot;
    }
    std::vector<TagInfo> tags;
    int flagAdd = 1;
    std::string spineSrc;
    std::string anchorId;
    std::string endAnchorId;
    xml_ext::parse(mainEle, docTexts, anchorId, endAnchorId, &flagAdd, spineSrc);
    LOGD("%s::parse done, docTexts.size = %zu", __func__, docTexts.size());
    if (docTexts.empty() && anchorId.empty()) {
        auto eleRoot = chapterDoc.RootElement();
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
    mockFirstPage(chapter, docTexts, meta_info.title, meta_info.author, meta_info.publisher);
    handle_tags(env, docTexts, cssInfos);

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: invoke done duration = %lld ms", __func__, duration);
    return 1;
}

int32_t html_util::getWordCount(std::vector<ChapterCount> &wordCounts) {
    LOGI("%s:invoke", __func__);
    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }
    std::lock_guard<std::mutex> lock(m_Mutex3);
    auto start_time = std::chrono::high_resolution_clock::now();
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }

    std::string chapter_data;
    int dataSize = file_ext::readStringFromFile(book_path, chapter_data);
    if (dataSize <= 0) {
        LOGE("%s:failed to read file: %s", __func__, book_path.c_str());
        return 0;
    }

    size_t charCount = 0;
    size_t picCount = 0;
    std::string start;
    std::string end;
    string_ext::count_text_pic_from_html(chapter_data, start, end, charCount, picCount);

    wordCounts.emplace_back(ChapterCount{1, charCount, picCount});
    size_t total = charCount + picCount;

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: duration = %lld ms, total = %zu", __func__, duration, total);
    return total;
}

void html_util::handle_tags(JNIEnv *env, std::vector<DocText> &docTexts, std::vector<CssInfo> &cssInfos) {
    auto start_time = std::chrono::high_resolution_clock::now();
    LOGI("%s:invoke", __func__);
    for (auto &doctext: docTexts) {
        if (!doctext.tagInfos.empty()) {
            auto itag = doctext.tagInfos.begin();
            for(; itag != doctext.tagInfos.end(); ++itag) {
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
                            std::stringstream ss;
                            if (!params.empty()) {
                                ss << params;
                                ss << "&";
                            }
                            for (auto rule_data : rule_datas) {
                                if (rule_data.name == "background") {
                                    continue;
                                }
                                ss << rule_data.name << "=" << rule_data.value << "&";
                            }
                            std::string result = ss.str();
                            if (!result.empty() && result.back() == '&') {
                                result = result.substr(0, result.length() - 1);
                            }
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
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:invoke done duration = %lld ms", __func__, duration);
}
