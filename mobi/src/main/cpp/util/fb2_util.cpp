//
// Created by wxn on 2025/7/14.
//

#include "fb2_util.h"

int loadMetaInfo(tinyxml2::XMLDocument &doc, MetaInfo &meta_info){
    tinyxml2::XMLElement *root = doc.FirstChildElement("FictionBook");
    if (root == nullptr) {
        return -1;
    }

    tinyxml2::XMLElement *eleDesc = root->FirstChildElement("description");
    if (eleDesc == nullptr) {
        return -1;
    }

    auto eleTitleInfo = eleDesc->FirstChildElement("title-info");
    if (eleTitleInfo != nullptr) {
        auto eleTitle = eleTitleInfo->FirstChildElement("book-title");
        if (eleTitle != nullptr) {
            meta_info.title = xml_ext::getEleText(eleTitle);
        }

        auto eleAnnotation= eleTitleInfo->FirstChildElement("annotation");
        if (eleAnnotation != nullptr) {
            meta_info.description = xml_ext::getEleText(eleAnnotation);
        }

        auto eleLang = eleTitleInfo->FirstChildElement("lang");
        meta_info.language = xml_ext::getEleText(eleLang);

        auto eleCoverPage = eleTitleInfo->FirstChildElement("coverpage");
        if (eleCoverPage != nullptr) {
            std::string imgSrc = xml_ext::get_img_src(eleCoverPage->FirstChildElement("image"));
            if (imgSrc.length() > 1 && string_ext::startWith(imgSrc, "#")) {
                std::string ref = imgSrc.substr(1);
                auto eleBinary = xml_ext::getChildByNameAndAttr(root, "binary", "id", ref);
                if (eleBinary != nullptr) {
                    std::string mimetype = xml_ext::getEleAttr(eleBinary, "content-type");
                    std::string ext = file_ext::get_media_type_ext(mimetype);
                    if (ext.empty()) {
                        ext = "png";
                    }
                    std::string cover_name = meta_info.title + "_" + meta_info.author;
                    std::string output_cover_path = file_ext::get_cover_path(cover_name, ext);
                    LOGD("%s:cover_path = %s", __func__, output_cover_path.c_str());
                    if (file_ext::checkPath(output_cover_path) != 1) {
                        std::string base64Data = xml_ext::getEleText(eleBinary);
                        string_ext::trim(base64Data);
                        if (base64Data.length() > 1) {
                            std::istringstream isstream(base64Data);

                            std::ofstream imgFileStream(output_cover_path, std::ios::binary);
                            base64::decoder b64decoder;
                            b64decoder.decode(isstream, imgFileStream);
                            meta_info.coverPath = output_cover_path;
                        }
                    }
                }
            }
        }

        auto eleAuthor = eleTitleInfo->FirstChildElement("author");
        if (eleAuthor != nullptr) {
            std::string text = xml_ext::getEleText(eleAuthor);
            std::string nickname = xml_ext::getEleText(eleAuthor->FirstChildElement("nickname"));
            std::string firstname = xml_ext::getEleText(eleAuthor->FirstChildElement("first-name"));
            std::string lastname = xml_ext::getEleText(eleAuthor->FirstChildElement("last-name"));

            if (!text.empty()) {
                meta_info.author = text;
            } else if (!nickname.empty()) {
                meta_info.author = nickname;
            } else if (!firstname.empty() && !lastname.empty()) {
                meta_info.author = firstname + " " + lastname;
            }
            string_ext::trim(meta_info.author);
        }
    }


    auto eleDocmentInfo = eleDesc->FirstChildElement("document-info");
    if (eleDocmentInfo != nullptr) {
        auto eleAuthor = eleDocmentInfo->FirstChildElement("author");
        if (meta_info.author.empty() && eleAuthor != nullptr) {
            std::string text = xml_ext::getEleText(eleAuthor);
            std::string nickname = xml_ext::getEleText(eleAuthor->FirstChildElement("nickname"));
            std::string firstname = xml_ext::getEleText(eleAuthor->FirstChildElement("first-name"));
            std::string lastname = xml_ext::getEleText(eleAuthor->FirstChildElement("last-name"));

            if (!text.empty()) {
                meta_info. author = text;
            } else if (!nickname.empty()) {
                meta_info. author = nickname;
            } else if (!firstname.empty() && !lastname.empty()) {
                meta_info. author = firstname + " " + lastname;
            }
            string_ext::trim(meta_info.author);
        }

        auto eleId = eleDocmentInfo->FirstChildElement("id");
        meta_info.review = xml_ext::getEleText(eleId);

        auto eleDate = eleDocmentInfo->FirstChildElement("date");
        meta_info. date = xml_ext::getEleText(eleDate);

        auto eleVersion = eleDocmentInfo->FirstChildElement("version");
        meta_info.imprint = xml_ext::getEleText(eleVersion);

//        auto eleProgramUsed = eleDocmentInfo->FirstChildElement("program-used");
//        meta_info. publisher = xml_ext::getEleText(eleProgramUsed);
    }

    auto elePublishInfo = eleDesc->FirstChildElement("publish-info");
    if (elePublishInfo != nullptr) {
        auto elePublisher = elePublishInfo->FirstChildElement("publisher");
        meta_info.publisher = xml_ext::getEleText(elePublisher);

        auto eleIsbn = elePublishInfo->FirstChildElement("isbn");
        meta_info.isbn = xml_ext::getEleText(eleIsbn);
    }

    return 1;
}

int fb2_util::load_fb2(const char* fullpath,
                    std::string& coverPath,
                    std::string& title,

                    std::string& author,
                    std::string& contributor,
                    std::string& subjecct,

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
                    bool& isEncripted
) {
    tinyxml2::XMLDocument doc;
    doc.ClearError();
    doc.Clear();
    if (doc.LoadFile(fullpath) != tinyxml2::XML_SUCCESS) {
        return -1;
    }
    MetaInfo metainfo;
    loadMetaInfo(doc, metainfo);

    coverPath = metainfo.coverPath;
    title = metainfo.title;
    author = metainfo.author;

    contributor = metainfo.contributor;
    subjecct = metainfo.subject;
    publisher = metainfo.publisher;

    date = metainfo.date;
    description = metainfo.description;
    review = metainfo.review;
    imprint = metainfo.imprint;
    copyright = metainfo.copyright;
    isbn = metainfo.isbn;
    asin = metainfo.asin;
    language = metainfo.language;
    identifier = metainfo.isbn;
    isEncripted = metainfo.isEncrypted;

    return 1;
}

int fb2_util::getChapters(/*out*/std::vector<NavPoint> &points) {
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

    auto root = doc.FirstChildElement("FictionBook");
    if (root == nullptr) {
        LOGE("%s:root[FictionBook] is null", __func__);
        return 0;
    }
    auto body = root->FirstChildElement("body");
    if (body == nullptr) {
        LOGE("%s:body is null", __func__);
        return 0;
    }

    tinyxml2::XMLElement* eleSection = body->FirstChildElement("section");
    int index = 0;
    do {
        index++;
        std::string title;
        xml_ext::get_ele_words(eleSection->FirstChildElement("p"), title);

        NavPoint chapter;
        chapter.text = title;
        chapter.src = "";
        chapter.id = string_ext::generate_uuid();
        chapter.playOrder = index;
        points.emplace_back(chapter);

        eleSection = eleSection->NextSiblingElement("section");
    } while(eleSection != nullptr);

    allChapters.clear();
    allChapters.insert(allChapters.end(), points.begin(), points.end());

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:invoke done duration = %lld ms", __func__, duration);
    return 1;
}

int fb2_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter,
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

    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    auto eleRoot = doc.FirstChildElement("FictionBook");
    if(eleRoot == nullptr) {
        LOGE("%s root[FictionBook] is null", __func__);
        return 0;
    }

    auto eleBody = eleRoot->FirstChildElement("body");
    if (eleBody == nullptr) {
        LOGE("%s body is null", __func__);
        return 0;
    }
    int index = chapter.playOrder - 1;
//    int index = chapter.playOrder;
    if (index < 0) {
        return 0;
    }
    auto eleSection = eleBody->FirstChildElement("section");
    while (index > 0) {
        if (eleSection == nullptr) {
            return 0;
        }
        eleSection = eleSection->NextSiblingElement("section");
        index--;
    }

    if (eleSection == nullptr) {
        return 0;
    }

    std::vector<TagInfo> tags;
    int flagAdd = 1;
    std::string spineSrc = "";
    std::string anchorId = "";
    std::string endAnchorId = "";
    xml_ext::parse(eleSection->FirstChildElement(), docTexts, anchorId, endAnchorId, &flagAdd, spineSrc, 1);
    LOGD("%s::parse done, docTexts.size = %zu", __func__, docTexts.size());

    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }
    mockFirstPage(chapter, docTexts, meta_info.title, meta_info.author, meta_info.publisher);
    handle_tags(env, docTexts);

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: invoke done duration = %lld ms", __func__, duration);
    return 1;

}

int fb2_util:: getCss(std::vector<std::string> &cssClasses, std::vector<std::string> &cssTags,
           std::vector<std::string> &cssIds, std::vector<CssInfo> &cssInfos) {
    return 1;
}

int32_t fb2_util::getWordCount(std::vector<ChapterCount> &wordCounts) {
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
    std::vector<NavPoint> chapters;
    if (getChapters(chapters) != 1) {
        return 0;
    }
    tinyxml2::XMLDocument doc2;

    if (doc2.LoadFile(this->book_path.c_str()) != tinyxml2::XML_SUCCESS) {
        return 0;
    }

    auto eleRoot = doc2.FirstChildElement("FictionBook");
    if (eleRoot == nullptr) {
        LOGE("%s root[FictionBook] is null", __func__);
        return 0;
    }

    tinyxml2::XMLElement *body = eleRoot->FirstChildElement("body");
    if (body == nullptr) {
        LOGE("%s failed parse html, no body element", __func__);
        return 0;
    }

    auto *eleSection = body->FirstChildElement("section");
    int index = 1;

    std::vector<std::pair<size_t, size_t>> counts;
    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }
    size_t total = 0;
    while(eleSection != nullptr) {
        size_t wordCount = 0;
        size_t picCount = 0;
        xml_ext::count_ele_words(eleSection, &wordCount, &picCount);
        wordCounts.emplace_back(ChapterCount{index,wordCount,picCount});

        eleSection = eleSection->NextSiblingElement("section");
        index++;
        total += wordCount;
        total += picCount;
    }
    return total;
}

void fb2_util::fb2_release() {
    LOGI("%s:invoke", __func__);
    std::lock_guard<std::mutex> lock(m_Mutex4);
    if (initStatus) {
        doc.Clear();
        doc.ClearError();
        initStatus = false;
    }
    LOGI("%s:invoke done", __func__);
}

int fb2_util::fb2_init() {
    LOGI("%s:invoke", __func__);
    if (book_id == 0L || book_path.empty()) {
        return 0;
    }

    doc.ClearError();
    doc.Clear();
    if (doc.LoadFile(book_path.c_str()) != tinyxml2::XML_SUCCESS) {
        return 0;
    }

    if (1 != loadMetaInfo(doc, meta_info)) {
        return 0;
    }

    return 1;
}


void fb2_util::handle_tags(JNIEnv *env, std::vector<DocText> &docTexts) {
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
                            width = string_ext::toInt(kv.second);
                        }
                    }
                    if (!imgSrc.empty()) {
                        int srcWidth = 0;
                        int srcHeight = 0;
                        if (1 == cache_image(env, imgSrc, &srcWidth, &srcHeight)) {
                            if (srcWidth > 0 && srcHeight > 0 && !imgSrc.empty()) {
                                std::stringstream ss;
                                int w = width, h = height;
                                if (srcWidth > width || srcHeight > height) {
                                    w = srcWidth;
                                    h = srcHeight;
                                }
                                ss <<  "src=" + imgSrc + "&width=" + std::to_string(w) + "&height=" + std::to_string(h);
                                for(auto &kv : kvs) {
                                    if (kv.first != "src" && kv.first != "width" && kv.first != "height") {
                                        ss << "&" << kv.first << "=" << kv.second;
                                    }
                                }
                                imgtag.params = ss.str();
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

/***
 * 缓存图片
 * @param env
 * @param imgSrc
 * @param width
 * @param height
 * @return
 */
int fb2_util::cache_image(JNIEnv *env, std::string &imgSrc, int *width, int *height) {
    std::string fullpath;
    std::string src = imgSrc;
    if (string_ext::startWith(src, "#")) {
        src = src.substr(1);
    }

    if (!src.empty()) {
        auto root = doc.FirstChildElement("FictionBook");
        if (root == nullptr) {
            return 0;
        }

        auto eleBinary = xml_ext::getChildByNameAndAttr(root, "binary", "id", src);
        if (eleBinary != nullptr) {
            std::string mimetype = xml_ext::getEleAttr(eleBinary, "content-type");
            std::string imgtype = file_ext::get_media_type_ext(mimetype);
            if (imgtype.empty()) {
                imgtype = "png";
            }

            //文件路径
            fullpath = file_ext::get_img_path(book_id, src + "." + imgtype);
            if (!run_flag) {
                LOGI("%s:invoke failed, run_flag false", __func__);
                return 0;
            }
            int ret = file_ext::checkAndCreateDir(file_ext::get_img_parent_path(book_id), src);
            if (ret < 0) {
                LOGE("%s:failed, creat dir err", __func__);
                return 0;
            } else if (ret == 0) {  //缓存文件不存在，缓存路径存在或者创建缓存路径成功
                std::string base64Data = xml_ext::getEleText(eleBinary);
                string_ext::trim(base64Data);
                base64Data = string_ext::cleanStr(base64Data);

                if (base64Data.length() > 1) {
                    std::istringstream isstream(base64Data);
                    std::ofstream imgFileStream(fullpath, std::ios::binary);
                    base64::decoder b64decoder;
                    b64decoder.decode(isstream, imgFileStream);
                }
            } else {
//                LOGE("%s failed, create src[%s] failed", __func__, fullpath.c_str());
//                return 0;
            }
        }
    } else {
        LOGE("%s failed, src is empty", __func__ );
        return 0;
    }

    //缓存文件已经存在
    bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
    imgSrc = fullpath;
    return 1;
}
