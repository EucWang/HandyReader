//
// Created by MAC on 2025/4/17.
//

#include "mobi_util.h"

void mobi_util::mobi_data_free() {
    if (mobi_rawml != nullptr) {
        mobi_free_rawml(mobi_rawml);
    }
    mobi_rawml = nullptr;
    if (mobi_data != nullptr) {
        mobi_free(mobi_data);
    }
    mobi_data = nullptr;
    initStatus = false;
}

int mobi_util::init() {
    const char *version = mobi_version();
    LOGI("%s mobi version = %s", __func__, version);
    mobi_data = mobi_init();
    if (mobi_data == nullptr) {
        LOGE("%s mobi_init failed", __func__);
        mobi_data_free();
        return MOBI_ERROR;
    }

    MOBI_RET mobi_ret = mobi_load_filename(mobi_data, book_path.c_str());
    if (mobi_ret != MOBI_SUCCESS) {
        const char *msg = libmobi_msg(mobi_ret);
        LOGE("%s mobi_load_file failed, msg[%s]", __func__, msg);
        mobi_data_free();
        return MOBI_ERROR;
    }

    mobi_rawml = mobi_init_rawml(mobi_data);
    if (mobi_rawml == NULL) {
        mobi_data_free();
        LOGE("%s mobi_init_rawml failed, rawml is null", __func__);
        return MOBI_ERROR;
    }

    mobi_ret = mobi_parse_rawml(mobi_rawml, mobi_data);
    if (mobi_ret != MOBI_SUCCESS) {
        const char *msg = libmobi_msg(mobi_ret);
        LOGE("%s mobi_parse_rawml failed, msg[%s]", __func__, msg);
        mobi_data_free();
        return MOBI_ERROR;
    }
    return MOBI_SUCCESS;
}

void parseNavPoints(tinyxml2::XMLElement *firstNavPoint, std::vector<NavPoint> &vectors, const char *parentId) {
    for (tinyxml2::XMLElement *navPoint = firstNavPoint; navPoint; navPoint = navPoint->NextSiblingElement("navPoint")) {
        const char *id = navPoint->Attribute("id");
        const char *playOrder = navPoint->Attribute("playOrder");
        const char *label = navPoint->FirstChildElement("navLabel")->FirstChildElement("text")->GetText();
        const char *src = navPoint->FirstChildElement("content")->Attribute("src");
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

int parseOpfData(const char *opf_data, size_t opf_data_size, std::vector<NavPoint> &points) {
    tinyxml2::XMLDocument doc;
    if (doc.Parse(reinterpret_cast<const char *>(opf_data), opf_data_size) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse opf", __func__);
        return 0;
    }

    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse opf, no root element", __func__);
        return 0;
    }

    auto spine = root->FirstChildElement("spine");
    if (spine == NULL) {
        LOGE("%s failed parse npf, no root element", __func__);
        return 0;
    }
    std::vector<std::string> itemrefs;
    for (auto item = spine->FirstChildElement("itemref"); item; item = item->NextSiblingElement("itemref")) {
        const char *idref = item->Attribute("idref");
        if (idref != NULL && strlen(idref) > 0) {
            itemrefs.push_back(idref);
        }
    }
    if (itemrefs.empty()) {
        LOGE("%s failed parse itemref", __func__);
        return 0;
    }

    auto manifest = root->FirstChildElement("manifest");
    if (manifest == NULL) {
        LOGE("%s failed parse opf, no manifest element", __func__);
        return 0;
    }
    int index = 0;
    std::vector<std::string> orderedItemSrc;
    for (auto item = manifest->FirstChildElement("item"); item; item = item->NextSiblingElement("item")) {
        const char *id = item->Attribute("id");
        const char *href = item->Attribute("href");
        const char *media_type = item->Attribute("media-type");

        if (id != NULL && strlen(id) > 0 && itemrefs[index] == id && href != NULL && strlen(href) > 0) {
            orderedItemSrc.push_back(href);
            index++;
        }
    }
    if (orderedItemSrc.empty()) {
        LOGE("%s failed parse opf, no ordered items", __func__);
        return 0;
    }

    index = 0;
    for (auto &itemSrc: orderedItemSrc) {
        if (index >= points.size()) {
            break;
        }
        NavPoint &point = points[index];
        std::string &pointSrc = point.src;
        if (pointSrc.find(itemSrc) != std::string::npos) {
            index++;
        } else {
            int last_index = index - 1;
            if (last_index >= 0) {
                NavPoint &last_point = points[last_index];
                last_point.src = last_point.src.append(",").append(itemSrc);
            }
        }
    }

    return 1;
}

int parseNcxData(const char *ncx_data, size_t ncx_data_size, std::vector<NavPoint> &points) {
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
    return 1;
}

int mobi_util::getChapters(JNIEnv *env, long book_id, const char *path, std::vector<NavPoint> &points) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__ );
        return 0;
    }

    if (!allChapters.empty()) {
        points.insert(points.end(), allChapters.begin(), allChapters.end());
        return 1;
    }

    const unsigned char *opf_data = NULL;
    const unsigned char *ncx_data = NULL;
    size_t opf_data_size = 0;
    size_t ncx_data_size = 0;
    if (mobi_rawml->resources != NULL) {
        MOBIPart *curr = mobi_rawml->resources;
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

        int ret = parseNcxData(reinterpret_cast<const char *>(ncx_data), ncx_data_size, points);
        if (ret == 0) {
            LOGE("%s failed, cant pass ncx", __func__);
            return 0;
        }
        ret = parseOpfData(reinterpret_cast<const char *>(opf_data), opf_data_size, points);
        if (ret == 0) {
            LOGE("%s failed, cant pass opf", __func__);
            return 0;
        }
    } else {
        return 0;
    }
    allChapters.clear();
    allChapters.insert(allChapters.end(), points.begin(), points.end());
    return 1;
}

int mobi_util::loadMobi(std::string fullpath,
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
    int ret = dump_cover2(mobi_data, meta_title, app_ext::appFileDir.c_str(), &targetPath);
    if (ret == SUCCESS) {
        coverPath = targetPath;
    }
    auto now2 = std::chrono::system_clock::now();
    auto timestamp2 = std::chrono::duration_cast<std::chrono::milliseconds>(now2.time_since_epoch()).count();
    LOGD("%s: dump_cover path is [%s], use time : [%lld]ms ", __func__, coverPath.c_str(), timestamp2 - timestamp);

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

    mobi_free(mobi_data);
    mobi_free_rawml(rawml);
    return SUCCESS;
}

size_t parseElement(const tinyxml2::XMLElement *elem, std::string &fullText, std::string &parent_uuid, size_t initialOffset, std::vector<TagInfo> &subTags) {
    size_t currentOffset = initialOffset;

    for (const tinyxml2::XMLNode *child = elem->FirstChild(); child != nullptr; child = child->NextSibling()) {
        if (child->ToText()) {
            const char *text = child->Value();
            fullText += text;
            currentOffset += utf8Count(text);
        } else if (child->ToElement()) {
            size_t childStart = currentOffset;
            const char *id = elem->Attribute("id");
            std::string tagId = "";
            if (id != nullptr) {
                tagId = id;
            }
            auto newTag = TagInfo{generate_uuid(), tagId, elem->Name(), currentOffset, currentOffset, parent_uuid, ""};

            currentOffset = parseElement(child->ToElement(), fullText, newTag.uuid, childStart, subTags);

            if (currentOffset > childStart) {
                newTag.endPos = currentOffset;
            }
            subTags.push_back(newTag);
        }
    }
    return currentOffset;
}

std::string processParagraph(const tinyxml2::XMLElement *pElem, std::vector<TagInfo> &subTags) {
    size_t offset = 0;
    std::string fullText;

    for (const tinyxml2::XMLNode *child = pElem->FirstChild(); child != nullptr; child = child->NextSibling()) {
        if (child->ToText()) {
            const char *text = child->Value();
            if (text != NULL && utf8Count(text) > 0) {
                fullText += text;
                offset += utf8Count(text);
            }
        } else if (child->ToElement()) {
            size_t childStart = offset;

            auto elem = child->ToElement();
            const char *id = elem->Attribute("id");

            std::string params;
            for(auto attri = elem->FirstAttribute(); attri != nullptr; attri = attri->Next()) {
                const char* attriName = attri->Name();
                const char* attriValue = attri->Value();
                if (attriName != nullptr && attriValue != nullptr && strlen(attriName) > 0 && strlen(attriValue) > 0) {
                    if (!params.empty()) {
                        params.append("&");
                    }
                    params.append(attriName).append("=").append(attriValue);
                }
            }

            std::string tagId = "";
            if (id != nullptr) {
                tagId = id;
            }
            auto newTag = TagInfo{generate_uuid(), tagId, elem->Name(), childStart, childStart, "", params};
            offset = parseElement(child->ToElement(), fullText, newTag.uuid, childStart, subTags);

            if (offset > childStart) {
                newTag.endPos = offset;
            }
            subTags.push_back(newTag);
        }
    }
    return fullText;
}

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
int mobi_util::parseSrcName(std::string &src,
                            std::string &prefix,
                            int *prefixType,
                            int *srcId,
                            std::string &anchorId,
                            std::string &suffix) {

    size_t pos = src.find_first_of('#');
    std::string srcName;
    std::string srcTypeName;
    const std::string flow = "flow";
    const std::string part = "part";
    const std::string resource = "resource";
    int type = 0;
    std::string uid;
    int srcUid = -1;

    if (pos != std::string::npos) { //有#号，则分割
        srcName = src.substr(0, pos);
        if (pos + 1 <= src.size()) {
            anchorId = src.substr(pos + 1);
        } else {
            anchorId = "";
        }
    } else {
        srcName = src;
    }
    LOGD("%s:srcName=%s,aId=%s", __func__, srcName.c_str(), anchorId.c_str());

    pos = srcName.find_first_of('.');
    if (pos != std::string::npos) {
        srcTypeName = srcName.substr(0, pos);
        if (pos + 1 <= srcName.size()) {
            suffix = srcName.substr(pos + 1);
        } else {
            suffix = "";
        }
    } else {
        srcTypeName = srcName;
    }
    LOGD("%s:srcTypeName=%s,srcTypeSuffix=%s", __func__, srcTypeName.c_str(), suffix.c_str());
    if (srcTypeName.empty()) {
        LOGE("%s:src[%s] is not html,get srcTypeName[%s]", __func__, src.c_str(), srcTypeName.c_str());
        return 0;
    }

    if (startWith(srcTypeName, flow)) {
        type = 1;
        if (flow.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(flow.size() + 1);
        }
        prefix = flow;
    } else if (startWith(srcTypeName, part)) {
        type = 2;
        if (part.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(part.size() + 1);
        }
        prefix = part;
    } else if (startWith(srcTypeName, resource)) {
        type = 3;
        if (resource.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(resource.size() + 1);
        }
        prefix = resource;
    } else {
        LOGE("%s:srcTypeName[%s] is not right type", __func__, srcTypeName.c_str());
        return 0;
    }
    if (uid.empty()) {
        LOGE("%s:failed:srcTypeName[%s] can't have uid", __func__, srcTypeName.c_str());
        return 0;
    }

    try {
        srcUid = std::stoi(uid);
    } catch (const std::invalid_argument &e) {
        LOGE("%s:failed, uid[%s] is not invalid", __func__, uid.c_str());
        return 0;
    } catch (const std::out_of_range &e) {
        LOGE("%s:failed, uid[%s] is out of range", __func__, uid.c_str());
        return 0;
    }
    if (srcUid < 0) {
        LOGE("%s:failed,srcUid[%d] is below zero", __func__, srcUid);
        return 0;
    }

    *prefixType = type;
    *srcId = srcUid;
    return 1;
}

int mobi_util::cacheImage(JNIEnv *env, long book_id,MOBIRawml* mobi_rawml, std::string &imgSrc, int prefixType, int srcUid, int *width, int *height) {
    //文件路径
    std::string parentPath = app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id);
    std::string fullpath = parentPath + separator + imgSrc;
    int ret = file_ext::checkAndCreateDir(parentPath, imgSrc);
    if (ret == 1) { //缓存文件已经存在
        bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
        return 1;
    } else if (ret == 0) {  //缓存文件不存在，缓存路径存在或者创建缓存路径成功
        if (prefixType == 3 && mobi_rawml->resources != NULL) {
            MOBIPart *curr = NULL;
            curr = mobi_rawml->resources;

            unsigned char *rawPic = NULL;
            size_t rawPicSize = 0;
            while (curr != NULL) {
                MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
                //T_JPG, /**< jpg */  T_GIF, /**< gif */ T_PNG, /**< png */ T_BMP, /**< bmp */
                if (curr->size > 0 &&
                    (file_meta.type == T_JPG || file_meta.type == T_GIF || file_meta.type == T_PNG || file_meta.type == T_BMP) &&
                    curr->uid == srcUid) {
                    rawPic = curr->data;
                    rawPicSize = curr->size;
                    break;
                }
                curr = curr->next;
            }

            if (rawPicSize > 0 || rawPic != NULL) {
                if(file_ext::writeDataToFile(fullpath, rawPic, rawPicSize) == 1) {
                    bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
                    return 1;
                } else {
                    return 0;
                }
            } else {
                LOGE("%s:failed,rawPicSize[%zu] is null or rawPic is null", __func__, rawPicSize);
                return 0;
            }
        } else {
            LOGE("%s:failed,prefixType[%d] or resources is null", __func__, prefixType);
            return 0;
        }
    } else {
        LOGE("%s:failed, creat dir err", __func__);
        return 0;
    }
    return 1;
}

int mobi_util::parseHtmlDoc(JNIEnv *env, long book_id,  MOBIRawml* mobi_rawml, tinyxml2::XMLElement *element, std::vector<DocText> &docTexts) {
    tinyxml2::XMLElement *elem = element;
    while (elem != nullptr) {
        std::string name = elem->Name();
        if (name == "div" || name == "ul" || name == "ol" || name == "p" || name == "li") {
            const char *divText = elem->GetText();
            if (divText != NULL && utf8Count(divText) > 0) {
                std::vector<TagInfo> tagInfos;
                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos);
                docText.text = text;
                if (!tagInfos.empty()) {
                    for (auto &tag: tagInfos) {
                        docText.tagInfos.push_back(tag);
                    }
                }
                if (docText.text.size() > 0 && docText.tagInfos.empty() && docTexts.empty()) {
                    docText.tagInfos.push_back(TagInfo{generate_uuid(), "", "h1", 0, utf8Count(docText.text), "", ""});
                }
                docTexts.push_back(docText);
            } else {
                int count = elem->ChildElementCount();
                tinyxml2::XMLElement *child = elem->FirstChildElement();
                if (count > 0 && child != nullptr) {
                    parseHtmlDoc(env, book_id, mobi_rawml, child, docTexts);
                }
            }
//        } else if (name == "p" || name == "li") {
//            std::vector<TagInfo> tagInfos;
//            DocText docText{"", tagInfos};
//            std::string text = processParagraph(elem, tagInfos);
//            if (!text.empty() || !tagInfos.empty()) {
//                docText.text = text;
//                if (!tagInfos.empty()) {
//                    for (auto &tag: tagInfos) {
//                        docText.tagInfos.push_back(tag);
//                    }
//                }
//                docTexts.push_back(docText);
//            }
        } else if (name == "h1" || name == "h2" || name == "h3" || name == "h4" || name == "h5" || name == "h6" || name == "h7") {
            const char *elemText = elem->GetText();
            if (elemText != NULL && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                DocText docText{elemText, tagInfos};
                docText.tagInfos.push_back(TagInfo{generate_uuid(), "", name, 0, utf8Count(docText.text), "", ""});
                docTexts.push_back(docText);
            }
        } else if (name == "strong" || name == "em" || name == "b") {
            const char *elemText = elem->GetText();
            if (elemText != NULL && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                DocText docText{elemText, tagInfos};
                docText.tagInfos.push_back(TagInfo{generate_uuid(), "", name, 0, utf8Count(docText.text), "", ""});
                docTexts.push_back(docText);
            }
        } else if (name == "a") {
            const char *elemText = elem->GetText();
            if (elemText != NULL && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                DocText docText{elemText, tagInfos};
                const char *id = elem->Attribute("id");
                std::string params;
                if (id != nullptr && strlen(id) > 0) {
                    params = params.append("id=").append(id);
                } else {
                    id = "";
                }
                const char *href = elem->Attribute("href");
                if (href != nullptr && strlen(href) > 0) {
                    if (!params.empty()) {
                        params = params.append("&");
                    }
                    params = params.append("href=").append(href);
                }

                docText.tagInfos.push_back(TagInfo{generate_uuid(), id, name, 0, utf8Count(docText.text), "", params});
                docTexts.push_back(docText);
            }
        } else if (name == "img") {
            const char *imgSrc = elem->Attribute("src");
            if (imgSrc != NULL && utf8Count(imgSrc) > 0) {
                std::string imgSrcStr = imgSrc;
                std::string prefix;
                std::string suffix;
                std::string anchorId;
                int prefixType;
                int srcUid;
                if (1 == parseSrcName(imgSrcStr, prefix, &prefixType, &srcUid, anchorId, suffix)) {
                    LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
                         __func__, imgSrc, prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);
                    int width = 0, height = 0;
                    cacheImage(env, book_id, mobi_rawml, imgSrcStr, prefixType, srcUid, &width, &height);
                    std::string fullpath = app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id) + separator + imgSrc;
                    if (width > 0 && height > 0) {
                        std::vector<TagInfo> tags;
                        DocText docText{"", tags};
                        std::string params = "src=" + fullpath + "&width=" + std::to_string(width) + "&height=" + std::to_string(height);
                        docText.tagInfos.emplace_back(TagInfo{generate_uuid(), "", "img", 0, 0, "", params});
                        docTexts.push_back(docText);
                    } else {
                        LOGE("%s:failed,image[%s] width[%d]height[%d] err", __func__, fullpath.c_str(), width, height);
                    }
                }
            }
        }
        elem = elem->NextSiblingElement();
    }
    return 1;
}

int mobi_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__ );
        return 0;
    }
    if (app_ext::appFileDir.empty()) {
        return 0;
    }
    std::string chapterSrc = chapter.src;
    LOGD("%s:chapterSrc=%s", __func__, chapterSrc.c_str());
    std::vector<std::string> srcs = split(chapterSrc, ',');
    for (auto src: srcs) {
        std::string prefix;
        std::string suffix;
        std::string anchorId;
        int prefixType;
        int srcUid;
        if (1 != parseSrcName(src, prefix, &prefixType, &srcUid, anchorId, suffix)) {
            return 0;
        }
        LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
             __func__, src.c_str(), prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);

        MOBIPart *curr = NULL;
        if (prefixType == 1 && mobi_rawml->flow != NULL) {
            curr = mobi_rawml->flow;
        } else if (prefixType == 2 && mobi_rawml->markup != NULL) {
            curr = mobi_rawml->markup;
        } else if (prefixType == 3 && mobi_rawml->resources != NULL) {
            curr = mobi_rawml->resources;
        } else {
            LOGE("%s: unknown type[%d] or rawml data is null, pass", __func__, srcUid);
            return 0;
        }

        unsigned char *rawHtml = NULL;
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

        unsigned char *normalizedHtml = NULL;
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
            unsigned char *errInfo = errbuf.bp;
            LOGE("%s:failed %s", __func__, errInfo);
            return 0;
        }

        if (normalizedHtml == NULL || normalizedHtmlSize <= 0) {
            LOGE("%s:failed, tidy html failed", __func__);
            normalizedHtmlSize = rawHtmlSize;
            normalizedHtml = rawHtml;
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
            parseHtmlDoc(env, book_id,mobi_rawml, firstElem, docTexts);
        }
    }

    return 1;
}