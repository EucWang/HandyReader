//
// Created by MAC on 2025/4/17.
//

#include "mobi_util.h"


long mobi_util::last_book_id = 0L;
std::string mobi_util::last_path = "";
MOBIRawml *mobi_util::mobi_rawml = nullptr;
MOBIData *mobi_util::mobi_data = nullptr;

//int mobi_util::convertToEpub(
//        std::string fullpath,
//        std::string appCacheDir,
//        std::string &epubPath
//) {
//    LOGI("%s fullPath=%s,appFileDir=$appFileDir,", __func__, fullpath.c_str());
//    MOBIData *mobi_data = mobi_init();
//    if (mobi_data == NULL) {
//        LOGE("%s mobi_init failed", __func__);
//        return ERROR;
//    }
//
//    FILE *file = fopen(fullpath.c_str(), "rb");
//    if (file == NULL) {
//        mobi_free(mobi_data);
//        LOGE("%s fopen failed", __func__);
//        return ERROR;
//    }
//
//    MOBI_RET mobi_ret = mobi_load_file(mobi_data, file);
//    fclose(file);
//    if (mobi_ret != MOBI_SUCCESS) {
//        const char *msg = libmobi_msg(mobi_ret);
//        LOGE("%s mobi_load_file failed, msg[%s]", __func__, msg);
//        mobi_free(mobi_data);
//        return ERROR;
//    }
//
//    MOBIRawml *rawml = mobi_init_rawml(mobi_data);
//    if (rawml == NULL) {
//        mobi_free(mobi_data);
//        LOGE("%s mobi_init_rawml failed, rawml is null", __func__);
//        return ERROR;
//    }
//
//    mobi_ret = mobi_parse_rawml(rawml, mobi_data);
//    if (mobi_ret != MOBI_SUCCESS) {
//        const char *msg = libmobi_msg(mobi_ret);
//        LOGE("%s mobi_parse_rawml failed, msg[%s]", __func__, msg);
//        mobi_free(mobi_data);
//        mobi_free_rawml(rawml);
//        return ERROR;
//    }
//
//    auto now = std::chrono::system_clock::now();
//    auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
//
//    std::string basename = generate_uuid();
////    ret = create_epub(rawml, fullpath.c_str());
////------------- convert mobi to epub
//    std::string zipfile = appCacheDir + "/" + basename + ".epub";
//    int ret = epub_rawml_parts(rawml, zipfile.c_str());
//    LOGD("Saving EPUB to %s\n", zipfile.c_str());
//
//    if (ret == ERROR) {
//        LOGE("%s create epub failed, mobi path is [%s]", __func__, fullpath.c_str());
//        mobi_free_rawml(rawml);
//        mobi_free(mobi_data);
//        return ERROR;
//    }
//    auto now2 = std::chrono::system_clock::now();
//    auto timestamp2 = std::chrono::duration_cast<std::chrono::milliseconds>(now2.time_since_epoch()).count();
//    LOGD("%s: create_epub spend time :: [%lld]ms", __func__, timestamp2 - timestamp);
////-------------
//    epubPath = zipfile;
//    return SUCCESS;
//}

int mobi_util::init(long book_id, const char *path) {
    LOGI("%s book_id=%ld, fullPath=%s", __func__, book_id, path);
    if (book_id != last_book_id || last_path.empty() || !last_path.compare(path) || mobi_data == NULL || mobi_rawml == NULL) {
        free_data();

        const char *version = mobi_version();
        LOGI("%s mobi version = %s", __func__, version);
        mobi_data = mobi_init();
        if (mobi_data == NULL) {
            LOGE("%s mobi_init failed", __func__);
            free_data();
            return MOBI_ERROR;
        }

        FILE *file = fopen(path, "rb");
        if (file == NULL) {
            LOGE("%s fopen failed", __func__);
            free_data();
            return MOBI_ERROR;
        }

        MOBI_RET mobi_ret = mobi_load_file(mobi_data, file);
        fclose(file);
        if (mobi_ret != MOBI_SUCCESS) {
            const char *msg = libmobi_msg(mobi_ret);
            LOGE("%s mobi_load_file failed, msg[%s]", __func__, msg);
            free_data();
            return MOBI_ERROR;
        }

        mobi_rawml = mobi_init_rawml(mobi_data);
        if (mobi_rawml == NULL) {
            free_data();
            LOGE("%s mobi_init_rawml failed, rawml is null", __func__);
            return MOBI_ERROR;
        }

        mobi_ret = mobi_parse_rawml(mobi_rawml, mobi_data);
        if (mobi_ret != MOBI_SUCCESS) {
            const char *msg = libmobi_msg(mobi_ret);
            LOGE("%s mobi_parse_rawml failed, msg[%s]", __func__, msg);
            free_data();
            return MOBI_ERROR;
        }
    }
    last_book_id = book_id;
    last_path = path;
    return MOBI_SUCCESS;
}

void mobi_util::free_data() {
    if (mobi_rawml != NULL) {
        mobi_free_rawml(mobi_rawml);
    }
    mobi_rawml = NULL;
    if (mobi_data != NULL) {
        mobi_free(mobi_data);
    }
    mobi_data = NULL;
}

void parseNavPoints(tinyxml2::XMLElement* firstNavPoint, std::vector<NavPoint> &vectors, const char* parentId) {
    for (tinyxml2::XMLElement *navPoint = firstNavPoint; navPoint; navPoint = navPoint->NextSiblingElement("navPoint")) {
        const char* id = navPoint->Attribute("id");
        const char* playOrder = navPoint->Attribute("playOrder");
        const char* label = navPoint->FirstChildElement("navLabel")->FirstChildElement("text")->GetText();
        const char* src = navPoint->FirstChildElement("content")->Attribute("src");
        NavPoint nav;
        nav.id = id;
        nav.playOrder = toInt(playOrder);
        nav.text = label;
        nav.src = src;
        nav.parentId = parentId;
        vectors.push_back(nav);

        if (navPoint->ChildElementCount("navPoint") > 0) {
            parseNavPoints(navPoint->FirstChildElement("navPoint"), vectors, id);
        }
    }
}

int mobi_util::getChapters(long book_id, const char *path, std::vector<NavPoint>& points) {
    if (init(book_id, path) != MOBI_SUCCESS) {
        return 0;
    }

    if (mobi_rawml->resources != NULL) {
        MOBIPart *curr = mobi_rawml->resources;
        const unsigned char *opf_data = NULL;
        const unsigned char *ncx_data = NULL;
        size_t opf_data_size = 0;
        size_t ncx_data_size = 0;
        while (curr != NULL) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0) {
                if (file_meta.type == T_NCX) {
                    ncx_data = curr->data;
                    ncx_data_size = curr->size;
                } else if (file_meta.type == T_OPF) {
                    opf_data = curr->data;
                    opf_data_size = curr->size;
                }
            }
            curr = curr->next;
        }

        if (opf_data == NULL || ncx_data == NULL) {
            LOGE("%s failed, cant find opf or ncx, pass", __func__);
            return 0;
        }

        tinyxml2::XMLDocument doc;
        if (doc.Parse(reinterpret_cast<const char *>(ncx_data), ncx_data_size) != tinyxml2::XML_SUCCESS) {
            LOGE("%s failed to parse ncx", __func__);
            return 0;
        }

        tinyxml2::XMLElement *root = doc.RootElement();
        if (!root) {
            LOGE("%s failed parse ncx, no root element", __func__);
            return 0;
        }

        tinyxml2::XMLElement *navMapElem = root->FirstChildElement("navMap");
        if (!navMapElem) {
            LOGE("%s failed parse ncx, no navMap element", __func__);
            return 0;
        }
        tinyxml2::XMLElement *firstNavPoint = navMapElem->FirstChildElement("navPoint");
        parseNavPoints(firstNavPoint, points, "");
    }

    return 1;
}

int mobi_util::loadMobi(std::string fullpath,
                        std::string appFileDir,
                        std::string &coverPath,
//                        std::string& epubPath,

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
                        bool &isEncrypted) {

    LOGI("%s fullPath=%s", __func__, fullpath.c_str());
    const char *version = mobi_version();
    LOGI("%s mobi version = %s", __func__, version);
    MOBIData *mobi_data = mobi_init();
    if (mobi_data == NULL) {
        LOGE("%s mobi_init failed", __func__);
        return ERROR;
    }

    FILE *file = fopen(fullpath.c_str(), "rb");
    if (file == NULL) {
        mobi_free(mobi_data);
        LOGE("%s fopen failed", __func__);
        return ERROR;
    }

    MOBI_RET mobi_ret = mobi_load_file(mobi_data, file);
    fclose(file);
    if (mobi_ret != MOBI_SUCCESS) {
        const char *msg = libmobi_msg(mobi_ret);
        LOGE("%s mobi_load_file failed, msg[%s]", __func__, msg);
        mobi_free(mobi_data);
        return ERROR;
    }

    MOBIRawml *rawml = mobi_init_rawml(mobi_data);
    if (rawml == NULL) {
        mobi_free(mobi_data);
        LOGE("%s mobi_init_rawml failed, rawml is null", __func__);
        return ERROR;
    }

    mobi_ret = mobi_parse_rawml(rawml, mobi_data);
    if (mobi_ret != MOBI_SUCCESS) {
        const char *msg = libmobi_msg(mobi_ret);
        LOGE("%s mobi_parse_rawml failed, msg[%s]", __func__, msg);
        mobi_free(mobi_data);
        mobi_free_rawml(rawml);
        return ERROR;
    }

    //do work with mobi_data and rawml
//    print_meta(mobi_data);
//    print_summary(mobi_data);
//    print_exth(mobi_data);
    char *meta_title = mobi_meta_get_title(mobi_data);
    char *meta_author = mobi_meta_get_author(mobi_data);
    char *meta_contributor = mobi_meta_get_contributor(mobi_data);

    char *meta_subject = mobi_meta_get_subject(mobi_data);
    char *meta_publisher = mobi_meta_get_publisher(mobi_data);
    char *meta_date = mobi_meta_get_publishdate(mobi_data);

    char *meta_description = mobi_meta_get_description(mobi_data);
    char *meta_review = mobi_meta_get_review(mobi_data);
    char *meta_imprint = mobi_meta_get_imprint(mobi_data);

    char *meta_copyright = mobi_meta_get_copyright(mobi_data);
    char *meta_isbn = mobi_meta_get_isbn(mobi_data);
    char *meta_asin = mobi_meta_get_isbn(mobi_data);

    char *meta_language = mobi_meta_get_language(mobi_data);
    bool meta_isEncrypted = mobi_is_encrypted(mobi_data);

    /* Mobi header */
    char *meta_identifier = nullptr;
    if (mobi_data->mh) {
        meta_identifier = mobi_data->mh->mobi_magic;
    }

    if (meta_title) {
        title = meta_title;
    }
    if (meta_author) {
        author = meta_author;
    }
    if (meta_contributor) {
        contributor = meta_contributor;
    }
    if (meta_subject) {
        subject = meta_subject;
    }
    if (meta_publisher) {
        publisher = meta_publisher;
    }
    if (meta_date) {
        date = meta_date;
    }
    if (meta_description) {
        description = meta_description;
    }
    if (meta_review) {
        review = meta_review;
    }
    if (meta_imprint) {
        imprint = meta_imprint;
    }
    if (meta_copyright) {
        copyright = meta_copyright;
    }
    if (meta_isbn) {
        isbn = meta_isbn;
    }
    if (meta_asin) {
        asin = meta_asin;
    }
    if (meta_language) {
        language = meta_language;
    }
    if (meta_identifier) {
        identifier = meta_identifier;
    } else {
        identifier = "";
    }
    isEncrypted = meta_isEncrypted;

    auto now = std::chrono::system_clock::now();
    auto timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(now.time_since_epoch()).count();
    char cover_path[4096];
    char *targetPath = cover_path;
    int ret = dump_cover2(mobi_data, meta_title, appFileDir.c_str(), &targetPath);
    if (ret == SUCCESS) {
        coverPath = targetPath;
    }
    auto now2 = std::chrono::system_clock::now();
    auto timestamp2 = std::chrono::duration_cast<std::chrono::milliseconds>(now2.time_since_epoch()).count();
    LOGD("%s: dump_cover path is [%s], use time : [%lld]ms ", __func__, coverPath.c_str(), timestamp2 - timestamp);

//    ret = create_epub(rawml, fullpath.c_str());
//------------- convert mobi to epub
//    char zipfile[FILENAME_MAX];
//    memset(zipfile, 0, FILENAME_MAX);
//    if (create_path(zipfile, sizeof(zipfile), fullpath.c_str(), ".epub") == ERROR) {
//        return ERROR;
//    }
//    ret = epub_rawml_parts(rawml, zipfile);
//    LOGD("Saving EPUB to %s\n", zipfile);
//
//    if (ret == ERROR) {
//        LOGE("%s create epub failed, mobi path is [%s]", __func__, fullpath.c_str());
//        mobi_free_rawml(rawml);
//        mobi_free(mobi_data);
//        return ERROR;
//    } else {
//        epubPath = replaceExtension(fullpath, ".epub");
//    }
//    auto now3 = std::chrono::system_clock::now();
//    auto timestamp3 = std::chrono::duration_cast<std::chrono::milliseconds>(now3.time_since_epoch()).count();
//    LOGD("%s: create_epub spend time :: [%lld]ms", __func__, timestamp3 - timestamp2);
//-------------

    if (meta_title) {
        free(meta_title);
    }

    if (meta_author) {
        free(meta_author);
    }
    if (meta_contributor) {
        free(meta_contributor);
    }
    if (meta_subject) {
        free(meta_subject);
    }
    if (meta_publisher) {
        free(meta_publisher);
    }
    if (meta_date) {
        free(meta_date);
    }

    if (meta_description) {
        free(meta_description);
    }
    if (meta_review) {
        free(meta_review);
    }
    if (meta_imprint) {
        free(meta_imprint);
    }

    if (meta_copyright) {
        free(meta_copyright);
    }
    if (meta_isbn) {
        free(meta_isbn);
    }
    if (meta_asin) {
        free(meta_asin);
    }

    if (meta_language) {
        free(meta_language);
    }
    mobi_free_rawml(rawml);
    mobi_free(mobi_data);
    return SUCCESS;
}

// 递归收集元素的文本内容
//std::string processParagraph(const tinyxml2::XMLElement* elem) {
//    std::stringstream ss;
//
//    // 收集当前元素的文本
//    if (elem->GetText()) {
//        ss << elem->GetText();
//    }
//
//    // 递归处理子节点
//    for (const tinyxml2::XMLNode* child = elem->FirstChild(); child != nullptr; child = child->NextSibling()) {
//        if (child->ToText()) {  // 直接文本节点
//            ss << child->Value();
//        } else if (child->ToElement()) {  // 子元素
//            ss << collectText(child->ToElement());
//        }
//    }
//
//    return ss.str();
//}

size_t parseElement(const tinyxml2::XMLElement* elem, std::string& fullText, std::string& parent_uuid, size_t initialOffset, std::vector<TagInfo>& subTags) {
    size_t currentOffset = initialOffset;

    for(const tinyxml2::XMLNode* child = elem->FirstChild(); child != nullptr; child = child->NextSibling()) {
        if (child->ToText()) {
            const char* text = child->Value();
            fullText += text;
            currentOffset += utf8Count(text);
        } else if(child->ToElement()) {
            size_t childStart = currentOffset;
            const char* id = elem->Attribute("id");
            std::string tagId = "";
            if (id != nullptr) {
                tagId = id;
            }
            auto newTag = TagInfo{ generate_uuid(), tagId,elem->Name(),currentOffset,currentOffset, parent_uuid, ""};

            currentOffset = parseElement(child->ToElement(), fullText, newTag.uuid, childStart, subTags);

            if (currentOffset > childStart) {
                newTag.endPos = currentOffset;
            }
            subTags.push_back(newTag);
        }
    }
    return currentOffset;
}

std::string processParagraph(const tinyxml2::XMLElement* pElem, std::vector<TagInfo>& subTags) {
    size_t offset = 0;
    std::string fullText;

    for (const tinyxml2::XMLNode* child = pElem->FirstChild(); child != nullptr; child = child->NextSibling()) {
        if (child->ToText()) {
            const char* text = child->Value();
            fullText += text;
            offset += utf8Count(text);
        } else if (child->ToElement()) {
            size_t childStart = offset;

            auto elem = child->ToElement();
            const char* id = elem->Attribute("id");
            std::string tagId = "";
            if (id != nullptr) {
                tagId = id;
            }
            auto newTag = TagInfo{ generate_uuid(), tagId,elem->Name(),childStart,childStart, "", ""};
            offset = parseElement(child->ToElement(), fullText, newTag.uuid, childStart, subTags);

            if (offset > childStart) {
                subTags.back().endPos = offset;
            }
            subTags.push_back(newTag);
        }
    }
    return fullText;
}


int parseHtmlDoc(tinyxml2::XMLElement *element, std::vector<DocText>& docTexts) {
    tinyxml2::XMLElement* elem = element;
    while(elem != nullptr) {
        std::string name = elem->Name();
        if (name == "div") {
            int count = elem->ChildElementCount();
            tinyxml2::XMLElement * child = elem->FirstChildElement();
            if (count > 0 && child != nullptr) {
                parseHtmlDoc(child, docTexts);
            }
        } else if (name == "p") {
            std::vector<TagInfo> tagInfos;
            DocText docText{"", tagInfos};
            std::string text = processParagraph(elem, tagInfos);
            docText.text = text;
            if (!tagInfos.empty()) {
                for(auto& tag : tagInfos) {
                    docText.tagInfos.push_back(tag);
                }
            }
            docTexts.push_back(docText);
        } else if (name == "h1") {
            std::vector<TagInfo> tagInfos;
            DocText docText{"", tagInfos};
            std::string text = elem->GetText();
            docText.text = text;
            docText.tagInfos.push_back(TagInfo{generate_uuid(), "", "h1", 0, utf8Count(text), "", ""});
            docTexts.push_back(docText);
        } else if (name == "a") {
            std::vector<TagInfo> tagInfos;
            DocText docText{"", tagInfos};
            std::string text = elem->GetText();
            docText.text =text;
            const char* id = elem->Attribute("id");
            if (id == nullptr) {
                id = "";
            }
            docText.tagInfos.push_back(TagInfo{generate_uuid(), id, "a", 0, utf8Count(docText.text), "", ""});
            docTexts.push_back(docText);
        }
        elem = elem->NextSiblingElement();
    }
    return 1;
}

int mobi_util::getChapter(long book_id, const char *path, const char *app_file_dir, NavPoint& chapter, std::vector<DocText> &docTexts) {
    if (init(book_id, path) != MOBI_SUCCESS) {
        return 0;
    }

    std::string src = chapter.src;
    size_t pos = src.find_first_of('#');
    std::string srcName;
    std::string aId;
    if (pos != std::string::npos) { //有#号，则分割
        srcName = src.substr(0, pos);
        if (pos +1 <= src.size()) {
            aId = src.substr(pos+1);
        } else {
            aId = "";
        }
    } else {
        srcName = src;
    }
    LOGD("%s:srcName=%s,aId=%s", __func__, srcName.c_str(), aId.c_str());

    std::string srcTypeName;
    std::string srcTypeSuffix;

    pos = srcName.find_first_of('.');
    if (pos != std::string::npos) {
        srcTypeName = srcName.substr(0, pos);
        if (pos + 1 <= srcName.size()) {
            srcTypeSuffix = srcName.substr(pos + 1);
        } else {
            srcTypeSuffix = "";
        }
    } else {
        srcTypeName = srcName;
    }
    LOGD("%s:srcTypeName=%s,srcTypeSuffix=%s", __func__, srcTypeName.c_str(), srcTypeSuffix.c_str());
    if (srcTypeSuffix.empty() || srcTypeSuffix != "html") {
        LOGE("%s:src[%s] is not html,get srcTypeName[%s]", __func__, src.c_str(), srcTypeName.c_str());
        return 0;
    }

    const std::string flow = "flow";
    const std::string part = "part";
    const std::string resource = "resource";
    int type = 0;
    std::string uid;

    if (startWith(srcTypeName, flow)) {
        type = 1;
        if (flow.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(flow.size() + 1);
        }
    } else if (startWith(srcTypeName, part)) {
        type = 2;
        if (part.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(part.size() + 1);
        }
    } else if (startWith(srcTypeName, resource)) {
        type = 3;
        if (resource.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(resource.size() + 1);
        }
    } else {
        LOGE("%s:srcTypeName[%s] is not right type", __func__, srcTypeName.c_str());
        return 0;
    }
    if (uid.empty()) {
        LOGE("%s:failed:srcTypeName[%s] can't have uid", __func__, srcTypeName.c_str());
        return 0;
    }

    int srcUid = -1;
    try {
        srcUid = std::stoi(uid);
    } catch(const std::invalid_argument& e) {
        LOGE("%s:failed, uid[%s] is not invalid", __func__, uid.c_str());
        return 0;
    } catch(const std::out_of_range& e) {
        LOGE("%s:failed, uid[%s] is out of range", __func__, uid.c_str());
        return 0;
    }
    if (srcUid < 0) {
        LOGE("%s:failed,srcUid is below zero", __func__, srcUid);
        return 0;
    }
    MOBIPart *curr = NULL;
    if (type == 1 && mobi_rawml->flow != NULL) {
        curr = mobi_rawml->flow;
    } else if (type == 2 && mobi_rawml->markup != NULL) {
        curr = mobi_rawml->markup;
    } else if (type == 3 && mobi_rawml->resources != NULL) {
        curr = mobi_rawml->resources;
    } else {
        LOGE("%s: unknown type[%d] or rawml data is null, pass", __func__, srcUid);
        return 0;
    }

    unsigned char* rawHtml = NULL;
    size_t rawHtmlSize = 0;
    while (curr != NULL) {
        MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
        if (curr->size > 0 && file_meta.type == T_HTML && curr->uid == srcUid) {
            rawHtml = curr->data;
            rawHtmlSize = curr->size;
            break;
        }
        curr = curr->next;
    }

    if (rawHtmlSize <= 0 || rawHtml == NULL) {
        LOGE("%s: failed, unfound chapter page data.", __func__);
        return 0;
    }

    unsigned char* normalizedHtml = NULL;
    size_t normalizedHtmlSize = 0;
    TidyDoc tdoc = tidyCreate();
    TidyBuffer output = {0};
    TidyBuffer errbuf = {0};

    //tidy options
    tidyOptSetBool(tdoc, TidyXmlOut, yes); //output xhtml
    tidyOptSetBool(tdoc, TidyQuiet, yes);   //抑制警告
    tidyOptSetInt(tdoc, TidyWrapLen, 0);                //禁用换行
    tidyOptSetValue(tdoc, TidyCharEncoding, "utf8");    //编码集

    tidyParseString(tdoc, reinterpret_cast<ctmbstr>(rawHtml));
    if (tidyCleanAndRepair(tdoc) >= 0 && tidySaveBuffer(tdoc, &output) >= 0) {
        normalizedHtml = output.bp;
        normalizedHtmlSize = output.size;
    } else {
        unsigned char* errInfo = errbuf.bp;
        LOGE("%s:failed %s", __func__, errInfo);
        return 0;
    }

    if (normalizedHtml == NULL || normalizedHtmlSize <= 0) {
        LOGE("%s:failed, tidy html failed", __func__);
        return 0;
    }
    LOGD("%s:normalizedHtmlSize=%zu", __func__, normalizedHtmlSize);

    tinyxml2::XMLDocument doc;
    if (doc.Parse(reinterpret_cast<const char *>(normalizedHtml), normalizedHtmlSize) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse ncx", __func__);
        return 0;
    }

    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse ncx, no root element", __func__);
        return 0;
    }

    auto body = root->FirstChildElement("body");
    if (!body) {
        LOGE("%s failed parse html, no body element", __func__);
        return 0;
    }

    auto firstElem = body->FirstChildElement();

    if (firstElem != nullptr) {
        parseHtmlDoc(firstElem, docTexts);
    }

    return 1;
}

