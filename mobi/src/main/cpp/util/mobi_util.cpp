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
    if (mobi_rawml == nullptr) {
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

int mobi_util::parseOpfData(const char *opf_data, size_t opf_data_size, std::vector<NavPoint> &points) {
    tinyxml2::XMLDocument doc;
    if (doc.Parse(std::string(opf_data, opf_data + opf_data_size).c_str(), opf_data_size) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse opf", __func__);
        return 0;
    }

    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse opf, no root element", __func__);
        return 0;
    }

    auto spine = root->FirstChildElement("spine");
    if (spine == nullptr) {
        LOGE("%s failed parse npf, no root element", __func__);
        return 0;
    }
    std::vector<std::string> itemrefs;
    for (auto item = spine->FirstChildElement("itemref"); item; item = item->NextSiblingElement("itemref")) {
        const char *idref = item->Attribute("idref");
        if (idref != nullptr && strlen(idref) > 0) {
            itemrefs.emplace_back(idref);
        }
    }
    if (itemrefs.empty()) {
        LOGE("%s failed parse itemref", __func__);
        return 0;
    }

    auto manifest = root->FirstChildElement("manifest");
    if (manifest == nullptr) {
        LOGE("%s failed parse opf, no manifest element", __func__);
        return 0;
    }
    int index = 0;
    std::vector<std::string> orderedItemSrc;
    for (auto item = manifest->FirstChildElement("item"); item; item = item->NextSiblingElement("item")) {
        const char *id = item->Attribute("id");
        const char *href = item->Attribute("href");
//        const char *media_type = item->Attribute("media-type");

        if (id != nullptr && strlen(id) > 0 && itemrefs[index] == id && href != nullptr && strlen(href) > 0) {
            orderedItemSrc.emplace_back(href);
            index++;
        }
    }
    if (orderedItemSrc.empty()) {
        LOGE("%s failed parse opf, no ordered items", __func__);
        return 0;
    }

    //指向相同位置的章节合并
    std::vector<NavPoint> tmp;
    for (int i = 0; i < points.size() - 1; ++i) {
        auto &point = points[i];
        auto &nextPoint = points[i + 1];
        if (point.src == nextPoint.src) {
            point.text.append(" ").append(nextPoint.text);
            tmp.push_back(point);
            i++;
        } else {
            tmp.push_back(point);
            if (i == points.size() - 2) {
                tmp.push_back(nextPoint);
            }
        }
    }
    if (tmp.size() != points.size()) {
        points.clear();
        points.insert(points.end(), tmp.begin(), tmp.end());
        int order = 1;
        for (auto &point: points) {
            point.playOrder = order++;
        }
    }

    //防止前面有遗漏章节
    index = 0;
    int startOpfIndex = 0;
    std::vector<NavPoint> newPoints;
    while (index < points.size() && startOpfIndex < orderedItemSrc.size()) {
        auto &point = points[index];
        auto &opf = orderedItemSrc[startOpfIndex];

        if (point.src.find(opf) != std::string::npos) { //找到了
            if (startOpfIndex < orderedItemSrc.size() - 1) {
                startOpfIndex++;
            }
        } else {
            //没有找到, 则在opf中往前找
            int opfIndex = startOpfIndex - 1;
            bool found = false;
            while (opfIndex >= 0) {
                auto &prevOpf = orderedItemSrc[opfIndex];
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
                    auto &nextOpf = orderedItemSrc[opfIndex];
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
                        newpoint.src = orderedItemSrc[i];
                        newpoint.text = "";
                        newpoint.parentId = "";
                        newpoint.id = generate_uuid();
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
            auto &opf = orderedItemSrc[i];
            if (lastPoint.src.find(opf) != std::string::npos) {
                continue;
            } else {
                NavPoint point;
                point.src = opf;
                point.text = "";
                point.id = generate_uuid();
                point.parentId = "";
                newPoints.push_back(point);
            }
        }
    } else if (index < points.size() && startOpfIndex >= orderedItemSrc.size()) {
        for (int i = index; i < points.size(); i++) {
            newPoints.push_back(points[i]);
        }
    }

    if (orderedItemSrc.size() == 1 && points.size() > 1) { //全部都在一个资源文件中
        std::string &src = points[0].src;
        std::string prefix;
        std::string spineSrc;
        std::string suffix;
        std::string anchorId;
        int prefixType;
        int srcUid;
        if (1 != parseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
            return 0;
        }
        if (!anchorId.empty()) { //第一章，不是从资源最开始位置开始的
            NavPoint point;
            point.src = orderedItemSrc[0];
            point.text = "";
            point.id = generate_uuid();
            point.parentId = "";
            newPoints.insert(newPoints.begin(), point);
        }
    }

    int order = 1;
    for (auto &point: newPoints) {
        point.playOrder = order++;
    }

    points.clear();
    points.insert(points.end(), newPoints.begin(), newPoints.end());
    return 1;
}

int parseNcxData(const char *ncx_data, size_t ncx_data_size, std::vector<NavPoint> &points) {
    tinyxml2::XMLDocument doc;
    if (doc.Parse(std::string(ncx_data, ncx_data + ncx_data_size).c_str(), ncx_data_size) != tinyxml2::XML_SUCCESS) {
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

int mobi_util::getChapters(std::vector<NavPoint> &points) {
    std::lock_guard<std::mutex> lock(m_Mutex2);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }

    if (!allChapters.empty()) {
        points.insert(points.end(), allChapters.begin(), allChapters.end());
        return 1;
    }


    const unsigned char *opf_data = nullptr;
    const unsigned char *ncx_data = nullptr;
    size_t opf_data_size = 0;
    size_t ncx_data_size = 0;
    if (mobi_rawml->resources != nullptr) {
        MOBIPart *curr = mobi_rawml->resources;
        while (curr != nullptr) {
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

        if (opf_data == nullptr || ncx_data == nullptr) {
            LOGE("%s failed, cant find opf or ncx, pass", __func__);
            return 0;
        }

        int ret = parseNcxData(std::string(ncx_data, ncx_data + ncx_data_size).c_str(), ncx_data_size, points);
        if (ret == 0) {
            LOGE("%s failed, cant pass ncx", __func__);
            return 0;
        }

        ret = parseOpfData(std::string(opf_data, opf_data + opf_data_size).c_str(), opf_data_size, points);
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
    if (mobi_data == nullptr) {
        LOGE("%s mobi_init failed", __func__);
        return ERROR;
    }

    FILE *file = fopen(fullpath.c_str(), "rb");
    if (file == nullptr) {
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
    if (rawml == nullptr) {
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

std::string
mobi_util::getEleParams(const tinyxml2::XMLElement *elem, std::string &spineSrcName) {
    std::string params;
    for (auto attri = elem->FirstAttribute(); attri != nullptr; attri = attri->Next()) {
        const char *attriName = attri->Name();
        const char *attriValue = attri->Value();
        if (attriName == nullptr || attriValue == nullptr || strlen(attriName) == 0) {
            continue;
        }
        std::string name(attriName, attriName + strlen(attriName));
        std::string value(attriValue, attriValue + strlen(attriValue));
        if (name.empty()) {
            continue;
        }

        if (name == "href") {
            if (!value.empty()) {
                if (!startWith(value, "http")) {

                    std::string &src = value;
                    std::string prefix;
                    std::string spineSrc;
                    std::string suffix;
                    std::string anchorId;
                    int prefixType;
                    int srcUid;
                    bool isSrcName = true;
                    if (1 != parseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
                        isSrcName = false;
                    } else if ((prefix != "flow" && prefix != "part" && prefix != "resource") &&
                               (prefixType != 1 && prefixType != 2 && prefixType != 3)) {
                        isSrcName = false;
                    }

                    std::string href;
                    if (!isSrcName) {
                        href.append(spineSrcName);
                        if (!startWith(value, "#")) {
                            href.append("#");
                        }
                    }
                    href.append(value);
                    value = href;
                }
            }
        }


        if (!params.empty()) {
            params.append("&");
        }

        params.append(name).append("=").append(value);
    }
    return params;
}

size_t
mobi_util::parseElement(const tinyxml2::XMLElement *elem, std::string &fullText, std::string &parent_uuid, size_t initialOffset, std::vector<TagInfo> &subTags,
                        std::string &startAnchorId,
                        std::string &endAnchorId,
                        int *flagAdd,
                        std::string &spineSrcName) {
    size_t currentOffset = initialOffset;

    for (const tinyxml2::XMLNode *child = elem->FirstChild(); child != nullptr; child = child->NextSibling()) {
        if (child->ToText()) {
            const char *text = child->Value();
            fullText += text;
            currentOffset += utf8Count(text);
        } else if (child->ToElement()) {
            auto item = child->ToElement();
            size_t childStart = currentOffset;
            const char *id = item->Attribute("id");

            std::string tagId = "";
            if (id != nullptr) {
                tagId = id;
            }

            if (!startAnchorId.empty() && startAnchorId == tagId) {
                *flagAdd = 1;
            } else if (!endAnchorId.empty() && endAnchorId == tagId) {
                *flagAdd = 2;
                break;
            }

            std::string params = getEleParams(item, spineSrcName);
            auto newTag = TagInfo{generate_uuid(), tagId, item->Name(), currentOffset, currentOffset, parent_uuid, params};

            size_t endOffset = parseElement(item, fullText, newTag.uuid, childStart, subTags, startAnchorId, endAnchorId, flagAdd, spineSrcName);

            if (endOffset >= childStart) {
                newTag.endPos = endOffset;
            }
            currentOffset = endOffset;
            subTags.push_back(newTag);
        }
    }
    return currentOffset;
}


std::string
mobi_util::processParagraph(const tinyxml2::XMLElement *pElem,
                            std::vector<TagInfo> &subTags,
                            std::string &startAnchorId,
                            std::string &endAnchorId,
                            int *flagAdd,
                            std::string &spineSrcName) {
    size_t offset = 0;
    std::string fullText;

    for (const tinyxml2::XMLNode *child = pElem->FirstChild(); child != nullptr; child = child->NextSibling()) {
        if (child->ToText()) {
            const char *text = child->Value();
            if (text != nullptr && utf8Count(text) > 0) {
                fullText += text;
                offset += utf8Count(text);
            }
        } else if (child->ToElement()) {
            size_t childStart = offset;

            auto elem = child->ToElement();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (!startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (!endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

            std::string params = getEleParams(elem, spineSrcName);

            auto newTag = TagInfo{generate_uuid(), aid, elem->Name(), childStart, childStart, "", params};
            offset = parseElement(child->ToElement(), fullText, newTag.uuid, childStart, subTags, startAnchorId, endAnchorId, flagAdd, spineSrcName);

            if (offset > childStart) {
                newTag.endPos = offset;
            }
            subTags.push_back(newTag);
        }
    }
    cleanStr(fullText);
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
                            std::string &spineSrc,
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
    spineSrc = srcName;
//    LOGD("%s:srcName=%s,aId=%s", __func__, srcName.c_str(), anchorId.c_str());

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
//    LOGD("%s:srcTypeName=%s,srcTypeSuffix=%s", __func__, srcTypeName.c_str(), suffix.c_str());
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

int mobi_util::cacheImage(JNIEnv *env, long book_id, MOBIRawml *mobi_rawml, std::string &imgSrc, int prefixType, int srcUid, int *width, int *height) {
    //文件路径
    std::string parentPath = app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id);
    std::string fullpath = parentPath + separator + imgSrc;
    int ret = file_ext::checkAndCreateDir(parentPath, imgSrc);
    if (ret == 1) { //缓存文件已经存在
        bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
        return 1;
    } else if (ret == 0) {  //缓存文件不存在，缓存路径存在或者创建缓存路径成功
        if (prefixType == 3 && mobi_rawml->resources != nullptr) {
            MOBIPart *curr = nullptr;
            curr = mobi_rawml->resources;

            unsigned char *rawPic = nullptr;
            size_t rawPicSize = 0;
            while (curr != nullptr) {
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

            if (rawPicSize > 0 || rawPic != nullptr) {
                if (file_ext::writeDataToFile(fullpath, rawPic, rawPicSize) == 1) {
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

/****
 * 判断子元素中是否有文本元素
 * @param elem
 * @return true: 有， false： 没有
 */
bool hasChildText(tinyxml2::XMLElement *elem) {
    auto child = elem->FirstChild();
    bool ret = false;
    while (child != nullptr) {
        if (child->ToText() != nullptr) {
            ret = true;
            break;
        } else if (child->ToElement() != nullptr) {
            auto childEle = child->ToElement();
            const char *name = childEle->Name();
            std::string nameStr(name, name + strlen(name));
            if (nameStr == "img" || nameStr == "p" || nameStr == "div" || nameStr == "blockquote") {
                ret = false;
                break;
            }
        }
        child = child->NextSibling();
    }
    return ret;
}

bool hasChildImg(tinyxml2::XMLElement *elem) {
    auto child = elem->FirstChild();
    bool ret = false;
    while (child != nullptr) {
        if (child->ToElement() != nullptr) {
            auto childElem = child->ToElement();
            const char *name = childElem->Name();
            if (name != nullptr) {
                std::string nameStr(name, name + strlen(name));
                if (nameStr == "img") {
                    ret = true;
                    break;
                }
            }
        }
        child = child->NextSibling();
    }
    return ret;
}

int mobi_util::parseHtmlDoc(JNIEnv *env,
                            long book_id,
                            MOBIRawml *mobi_rawml,
                            tinyxml2::XMLElement *element,
                            std::vector<DocText> &docTexts,
                            std::string &startAnchorId,
                            std::string &endAnchorId,
                            int *flagAdd,
                            std::string &spineSrcName,
                            std::vector<TagInfo> fatherTags) {
    tinyxml2::XMLElement *elem = element;
    while (elem != nullptr) {
        if (2 == *flagAdd) {
            break;
        }
        std::vector<TagInfo> parentTags;
        if (!fatherTags.empty()) {
            for (auto &tag: fatherTags) {
                parentTags.push_back(tag);
            }
        }

        std::string name = elem->Name();
        if (name == "p") {
            if (hasChildImg(elem)) {
                auto count = elem->ChildElementCount();
                tinyxml2::XMLElement *child = elem->FirstChildElement();
                if (count > 0 && child != nullptr) {
                    std::string params = getEleParams(elem, spineSrcName);
                    if (!params.empty()) {
                        TagInfo tag;
                        tag.name = name;;
                        tag.uuid = generate_uuid();
                        tag.startPos = 0;
                        tag.endPos = 0;
                        tag.params = params;
                        parentTags.emplace_back(tag);
                    }

                    parseHtmlDoc(env, book_id, mobi_rawml, child, docTexts, startAnchorId, endAnchorId, flagAdd, spineSrcName, parentTags);
                }
            } else {
                const char *id = elem->Attribute("id");
                std::string aid;
                if (id != nullptr && strlen(id) > 0) {
                    aid = id;
                }
                if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                    *flagAdd = 1;
                } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                    *flagAdd = 2;
                    break;
                }

                std::vector<TagInfo> tagInfos;
                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);

                docText.text = text;
                if (!tagInfos.empty()) {
                    for (auto &tag: tagInfos) {
                        docText.tagInfos.push_back(tag);
                    }
                }

                std::string params = getEleParams(elem, spineSrcName);
                if (!params.empty()) {
                    TagInfo tag;
                    tag.name = name;;
                    tag.uuid = generate_uuid();
                    tag.startPos = 0;
                    tag.endPos = utf8Count(text);
                    tag.params = params;
                    docText.tagInfos.emplace_back(tag);
                }

                if (!parentTags.empty()) {
                    for (auto &tag: parentTags) {
                        if (tag.endPos == 0) {
                            tag.endPos = utf8Count(text);
                        }
                        docText.tagInfos.push_back(tag);
                    }
                }

                if (*flagAdd == 1) {
                    docTexts.push_back(docText);
                }
            }
        } else if (name == "div" || name == "ul" || name == "ol" || name == "li" || name == "span" || name == "font" || name == "blockquote") {
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

//            const char *divText = elem->GetText();
            if (hasChildText(elem)) {
                std::vector<TagInfo> tagInfos;
                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);
                docText.text = text;
                if (!tagInfos.empty()) {
                    for (auto &tag: tagInfos) {
                        docText.tagInfos.push_back(tag);
                    }
                }

                std::string params = getEleParams(elem, spineSrcName);
                if (!params.empty()) {
                    TagInfo tag;
                    tag.name = name;;
                    tag.uuid = generate_uuid();
                    tag.startPos = 0;
                    tag.endPos = utf8Count(text);
                    tag.params = params;
                    docText.tagInfos.emplace_back(tag);
                }

                if (!parentTags.empty()) {
                    for (auto &tag: parentTags) {
                        if (tag.endPos == 0) {
                            tag.endPos = utf8Count(text);
                        }
                        docText.tagInfos.push_back(tag);
                    }
                }

                if (*flagAdd == 1) {
                    docTexts.push_back(docText);
                }
            } else {
                auto count = elem->ChildElementCount();
                tinyxml2::XMLElement *child = elem->FirstChildElement();
                if (count > 0 && child != nullptr) {
                    std::string params = getEleParams(elem, spineSrcName);
                    if (!params.empty()) {
                        TagInfo tag;
                        tag.name = name;;
                        tag.uuid = generate_uuid();
                        tag.startPos = 0;
                        tag.endPos = 0;
                        tag.params = params;
                        parentTags.emplace_back(tag);
                    }

                    parseHtmlDoc(env, book_id, mobi_rawml, child, docTexts, startAnchorId, endAnchorId, flagAdd, spineSrcName, parentTags);
                }
            }
        } else if (name == "h1" || name == "h2" || name == "h3" || name == "h4" || name == "h5" || name == "h6" || name == "h7") {
            const char *elemText = elem->GetText();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

            if (elemText != nullptr && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                std::string elemTextStr(elemText, elemText + strlen(elemText));
                cleanStr(elemTextStr);
                DocText docText{elemTextStr, tagInfos};

                auto tag = TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", getEleParams(elem, spineSrcName)};
                docText.tagInfos.push_back(tag);
                if (!parentTags.empty()) {
                    for (auto &tag: parentTags) {
                        if (tag.endPos == 0) {
                            tag.endPos = utf8Count(docText.text);
                        }
                        docText.tagInfos.push_back(tag);
                    }
                }

                if (1 == *flagAdd) {
                    docTexts.push_back(docText);
                }
            } else {
                std::vector<TagInfo> tagInfos;
                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);
                if (!text.empty() || !tagInfos.empty()) {
                    docText.text = text;
                    docText.tagInfos.push_back(TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", getEleParams(elem, spineSrcName)});
                    if (!tagInfos.empty()) {
                        for (auto &tag: tagInfos) {
                            docText.tagInfos.push_back(tag);
                        }
                    }
                    if (!parentTags.empty()) {
                        for (auto &tag: parentTags) {
                            if (tag.endPos == 0) {
                                tag.endPos = utf8Count(docText.text);
                            }
                            docText.tagInfos.push_back(tag);
                        }
                    }
                    docTexts.push_back(docText);
                }
            }
        } else if (name == "strong" || name == "em" || name == "b" || name == "i") {
            const char *elemText = elem->GetText();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }
            if (elemText != nullptr && utf8Count(elemText) > 0) {
                std::string elemTextStr(elemText, elemText + strlen(elemText));
                cleanStr(elemTextStr);
                std::vector<TagInfo> tagInfos;
                DocText docText{elemTextStr, tagInfos};
                docText.tagInfos.push_back(TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", getEleParams(elem, spineSrcName)});
                if (!parentTags.empty()) {
                    for (auto &tag: parentTags) {
                        if (tag.endPos == 0) {
                            tag.endPos = utf8Count(docText.text);
                        }
                        docText.tagInfos.push_back(tag);
                    }
                }
                if (1 == *flagAdd) {
                    docTexts.push_back(docText);
                }
            }
        } else if (name == "a") {
//            const char *elemText = elem->GetText();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

//            std::string text;
//            if (elemText != nullptr && strlen(elemText) > 0) {
//                text = elemText;
//            }
            std::vector<TagInfo> tagInfos;
            DocText docText{"", tagInfos};
            std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);
            docText.text = text;
            if (!tagInfos.empty()) {
                for (auto &tag: tagInfos) {
                    docText.tagInfos.push_back(tag);
                }
            }

            std::string params = getEleParams(elem, spineSrcName);
            docText.tagInfos.push_back(TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", params});
            if (!parentTags.empty()) {
                for (auto &tag: parentTags) {
                    if (tag.endPos == 0) {
                        tag.endPos = utf8Count(docText.text);
                    }
                    docText.tagInfos.push_back(tag);
                }
            }

            if (1 == *flagAdd) {
                docTexts.push_back(docText);
            }
        } else if (name == "img") {
            const char *imgSrc = elem->Attribute("src");
            if (imgSrc != nullptr && utf8Count(imgSrc) > 0) {
                std::string imgSrcStr = imgSrc;
                std::string prefix;
                std::string spineSrc;
                std::string suffix;
                std::string anchorId;
                int prefixType;
                int srcUid;
                if (1 == parseSrcName(imgSrcStr, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
//                    LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
//                         __func__, imgSrc, prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);
                    int width = 0, height = 0;
                    cacheImage(env, book_id, mobi_rawml, imgSrcStr, prefixType, srcUid, &width, &height);
                    std::string fullpath = app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id) + separator + imgSrc;
                    if (width > 0 && height > 0) {
                        std::vector<TagInfo> tags;
                        DocText docText{"", tags};
                        std::string params = "src=" + fullpath + "&width=" + std::to_string(width) + "&height=" + std::to_string(height);
                        docText.tagInfos.emplace_back(TagInfo{generate_uuid(), "", "img", 0, 0, "", params});
                        if (!parentTags.empty()) {
                            for (auto &tag: parentTags) {
                                if (tag.endPos == 0) {
                                    tag.endPos = utf8Count(docText.text);
                                }
                                docText.tagInfos.push_back(tag);
                            }
                        }
                        if (1 == *flagAdd) {
                            docTexts.push_back(docText);
                        }
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

int mobi_util::parseCssSrcList() {
    if (cssSrc.empty()) {
        const unsigned char *opf_data = nullptr;
        size_t opf_data_size = 0;
        if (mobi_rawml->resources == nullptr) {
            return 0;
        }
        MOBIPart *curr = mobi_rawml->resources;
        while (curr != nullptr) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0 && file_meta.type == T_OPF) {
                opf_data = curr->data;
                opf_data_size = curr->size;
                break;
            }
            curr = curr->next;
        }

        if (opf_data == nullptr) {
            LOGE("%s failed, cant find opf or ncx, pass", __func__);
            return 0;
        }

        tinyxml2::XMLDocument doc;

        if (doc.Parse(std::string(opf_data, opf_data + opf_data_size).c_str(), opf_data_size) != tinyxml2::XML_SUCCESS) {
            LOGE("%s failed to parse opf", __func__);
            return 0;
        }

        tinyxml2::XMLElement *root = doc.RootElement();
        if (!root) {
            LOGE("%s failed parse opf, no root element", __func__);
            return 0;
        }

        auto manifest = root->FirstChildElement("manifest");
        if (manifest == nullptr) {
            LOGE("%s failed parse opf, no manifest element", __func__);
            return 0;
        }

        MOBIFileMeta cssType = mobi_get_filemeta_by_type(T_CSS);
        for (auto item = manifest->FirstChildElement("item"); item; item = item->NextSiblingElement("item")) {
            const char *id = item->Attribute("id");
            const char *href = item->Attribute("href");
            const char *media_type = item->Attribute("media-type");

            if (id != nullptr && strlen(id) > 0 &&
                href != nullptr && strlen(href) > 0 &&
                media_type != nullptr && strlen(media_type) > 0 &&
                strncmp(cssType.mime_type, media_type, strlen(cssType.mime_type)) == 0) {
                cssSrc.emplace_back(href);
            }
        }
    }
    return 1;
}

int mobi_util::getCss(std::vector<std::string> &cssClasses, std::vector<CssInfo> &cssInfos) {
    std::lock_guard<std::mutex> lock(m_Mutex3);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }
    if (app_ext::appFileDir.empty()) {
        return 0;
    }

    if (parseCssSrcList() != 1) {
        LOGE("%s failed no css items", __func__);
        return 0;
    }
    if (cssSrc.empty()) {
        LOGE("%s failed get cssSrc, no css items", __func__);
        return 0;
    }

    future::CSSParser cssParser;

    for (auto &css: cssSrc) {
        std::string &src = css;
        std::string prefix;
        std::string spineSrc;
        std::string suffix;
        std::string anchorId;
        int prefixType;
        int srcUid;
        if (1 != parseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
            return 0;
        }
//        LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
//             __func__, src.c_str(), prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);

        MOBIPart *curr = nullptr;
        if (prefixType == 1 && mobi_rawml->flow != nullptr) {
            curr = mobi_rawml->flow;
        } else if (prefixType == 2 && mobi_rawml->markup != nullptr) {
            curr = mobi_rawml->markup;
        } else if (prefixType == 3 && mobi_rawml->resources != nullptr) {
            curr = mobi_rawml->resources;
        } else {
            LOGE("%s: unknown type[%d] or rawml data is null, pass", __func__, srcUid);
            return 0;
        }

        unsigned char *rawCss = nullptr;
        size_t rawCssSize = 0;
        while (curr != nullptr) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0 && file_meta.type == T_CSS && curr->uid == srcUid) {
                rawCss = curr->data;
                rawCssSize = curr->size;
                break;
            }
            curr = curr->next;
        }

        if (rawCss == nullptr || rawCssSize <= 0) {
            LOGE("%s failed, rawCss is null or rawCssSize is %d", __func__, rawCssSize);
            return 0;
        }
//        LOGD("%s:rawCssSize=%d", __func__, rawCssSize);

        std::string cssData(rawCss, rawCss + rawCssSize);
        cssParser.parseByString(cssData);
        const std::set<future::Selector *> &set = cssParser.getSelectors();
        for (auto it = set.begin(); it != set.end(); it++) {
            auto type = (*it)->getType();
            if (type == future::ClassSelector::SelectorType::ClassSelector) {
                auto *selector = dynamic_cast<future::ClassSelector *>(*it);
                auto cssid = selector->getClassIdentifier();
                if (std::find(cssClasses.begin(), cssClasses.end(), cssid) != cssClasses.end()) {
                    int weight = selector->weight();
                    bool isBaseSelector = selector->isBaseSelector();
                    std::string ruleData = selector->getRuleData();
                    std::vector<std::string> datas = split(ruleData, ';');
                    std::vector<RuleData> params;
                    if (!datas.empty()) {
                        for (auto &data: datas) {
                            trim(data);
                            std::vector<std::string> kv = split(data, ':');
                            if (kv.size() == 2) {
                                std::string k = trim_copy(kv[0]);
                                std::string v = trim_copy(kv[1]);
                                if (!k.empty() && !v.empty()) {
                                    params.emplace_back(RuleData{k, v});
                                }
                            }
                        }
                    }
                    cssInfos.emplace_back(CssInfo{cssid, weight, isBaseSelector, params});
                    if (cssInfos.size() >= cssClasses.size()) {
                        break;
                    }
                }
            }
        }
    }
    return 1;
}

int mobi_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter, std::vector<DocText> &docTexts) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }
    if (app_ext::appFileDir.empty()) {
        return 0;
    }
    std::string chapterSrc = chapter.src;
//    LOGD("%s:chapterSrc=%s", __func__, chapterSrc.c_str());
    std::string &src = chapterSrc;
    std::string prefix;
    std::string spineSrc;
    std::string suffix;
    std::string anchorId;
    int prefixType;
    int srcUid;
    if (1 != parseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
        return 0;
    }
//    LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
//         __func__, src.c_str(), prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);

    std::string endAnchorId;

    std::vector<NavPoint> points;
    int ret = getChapters(points);
    if (ret == 1) {
        int targetIndex = -1;
        for (int i = 0; i < points.size(); i++) { //找到下一章节的索引值
            if (points[i].src == chapterSrc) {
                targetIndex = i + 1;
                break;
            }
        }
        if (targetIndex >= 0 && targetIndex < points.size()) {
            NavPoint &nextChapter = points[targetIndex];
            std::string &nextSrc = nextChapter.src;
            std::string nextPrefix;
            std::string nextSpineSrc;
            std::string nextSuffix;
            std::string nextAnchorId;
            int nextPrefixType;
            int nextSrcUid;
            if (1 != parseSrcName(nextSrc, nextPrefix, nextSpineSrc, &nextPrefixType, &nextSrcUid, nextAnchorId, nextSuffix)) {
                return 0;
            }
            if (nextSrcUid == srcUid && !nextAnchorId.empty()) {
                endAnchorId = nextAnchorId;
//                LOGD("%s:startAnchorId=%s,endAnchorId=%s", __func__, anchorId.c_str(), endAnchorId.c_str());
            }
        }
    }

    if (spineSrc != currentSrc) {

        MOBIPart *curr = nullptr;
        if (prefixType == 1 && mobi_rawml->flow != nullptr) {
            curr = mobi_rawml->flow;
        } else if (prefixType == 2 && mobi_rawml->markup != nullptr) {
            curr = mobi_rawml->markup;
        } else if (prefixType == 3 && mobi_rawml->resources != nullptr) {
            curr = mobi_rawml->resources;
        } else {
            LOGE("%s: unknown type[%d] or rawml data is null, pass", __func__, srcUid);
            return 0;
        }

        unsigned char *rawHtml = nullptr;
        size_t rawHtmlSize = 0;
        while (curr != nullptr) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0 && file_meta.type == T_HTML && curr->uid == srcUid) {
                rawHtml = curr->data;
                rawHtmlSize = curr->size;
                break;
            }
            curr = curr->next;
        }

        if (rawHtmlSize <= 0 || rawHtml == nullptr) {
            LOGE("%s: failed, unfound chapter page data.", __func__);
            return 0;
        }

        unsigned char *normalizedHtml = nullptr;
        size_t normalizedHtmlSize = 0;
        TidyDoc tdoc = tidyCreate();
        TidyBuffer output = {0};
        TidyBuffer errbuf = {0};

        //tidy options
        tidyOptSetBool(tdoc, TidyXmlOut, yes); //output xhtml
        tidyOptSetBool(tdoc, TidyQuiet, yes);   //抑制警告
        tidyOptSetInt(tdoc, TidyWrapLen, 0);                //禁用换行
        tidyOptSetValue(tdoc, TidyCharEncoding, "utf8");    //编码集

        tidyParseString(tdoc, std::string(rawHtml, rawHtml + rawHtmlSize).c_str());
        if (tidyCleanAndRepair(tdoc) >= 0 && tidySaveBuffer(tdoc, &output) >= 0) {
            normalizedHtml = output.bp;
            normalizedHtmlSize = output.size;
        } else {
            unsigned char *errInfo = errbuf.bp;
            LOGE("%s:failed %s", __func__, errInfo);
            return 0;
        }

        if (normalizedHtml == nullptr || normalizedHtmlSize <= 0) {
            LOGE("%s:failed, tidy html failed", __func__);
            normalizedHtmlSize = rawHtmlSize;
            normalizedHtml = rawHtml;
        }
//        LOGD("%s:normalizedHtmlSize=%zu", __func__, normalizedHtmlSize);

        doc.ClearError();
        doc.Clear();
        if (doc.Parse(std::string(normalizedHtml, normalizedHtml + normalizedHtmlSize).c_str(), normalizedHtmlSize) != tinyxml2::XML_SUCCESS) {
            LOGE("%s failed to parse ncx", __func__);
            return 0;
        }

        currentSrc = spineSrc;
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

    int flagAdd = 0;
    if (anchorId.empty()) {
        flagAdd = 1;
    }

    if (firstElem != nullptr) {
        std::vector<TagInfo> tags;
        parseHtmlDoc(env, book_id, mobi_rawml, firstElem, docTexts, anchorId, endAnchorId, &flagAdd, spineSrc, tags);
        mockFirstPage(chapter, docTexts);
    }

    return 1;
}

/***
 * 第一章没有内容，由于合并ncx 和opf可能导致的首页没有内容，则需要填充一个默认的内容
 * @param chapter
 * @param docTexts
 */
void mobi_util::mockFirstPage(NavPoint &chapter, std::vector<DocText> &docTexts) {
    if (docTexts.empty() && chapter.playOrder == 1) {
        char *title = mobi_meta_get_title(mobi_data);
        char *author = mobi_meta_get_author(mobi_data);
        char *publisher = mobi_meta_get_publisher(mobi_data);
        if (title != nullptr) {
            std::vector<TagInfo> tagInfos;
            tagInfos.push_back(TagInfo{
                    generate_uuid(),
                    "",
                    "h1",
                    0,
                    strlen(title),
                    "",
                    ""
            });
            docTexts.emplace_back(DocText{std::string(title, title + strlen(title)), tagInfos});
        }
        if (author != nullptr) {
            std::vector<TagInfo> tagInfos;
            tagInfos.push_back(TagInfo{
                    generate_uuid(),
                    "",
                    "p",
                    0,
                    strlen(author),
                    "",
                    "align=center"
            });
            docTexts.emplace_back(DocText{std::string(author, author + strlen(author)), tagInfos});
        }
        if (publisher != nullptr) {
            std::vector<TagInfo> tagInfos;
            tagInfos.push_back(TagInfo{
                    generate_uuid(),
                    "",
                    "p",
                    0,
                    strlen(publisher),
                    "",
                    "align=center"
            });
            docTexts.emplace_back(DocText{std::string(publisher, publisher + strlen(publisher)), tagInfos});
        }
    }
}

void mobi_util::getWordCount(JNIEnv *env, std::vector<std::pair<int, int>> &wordCounts) {
    auto start_time = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> lock(m_Mutex4);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return;
    }
    std::vector<NavPoint> chapters;
    if (getChapters(chapters) != 1) {
        return;
    }
    std::string lastSpineSrc;
    size_t total = 0;
    for (auto item = chapters.begin(); item != chapters.end(); item++) {
        auto &chapter = (*item);

        std::string src = chapter.src;
        std::string prefix;
        std::string spineSrc;   //对应的资源文件名
        std::string suffix;
        std::string anchorId;
        int prefixType;
        int srcUid;
        if (1 != parseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
            return;
        }
        //下一章节的锚点
        std::string endAnchorId;
        auto nextItem = item + 1;
        if (nextItem != chapters.end()) {
            auto &nextChapter = *nextItem;
            std::string &nextSrc = nextChapter.src;
            std::string nextPrefix;
            std::string nextSpineSrc;
            std::string nextSuffix;
            std::string nextAnchorId;
            int nextPrefixType;
            int nextSrcUid;
            if (1 != parseSrcName(nextSrc, nextPrefix, nextSpineSrc, &nextPrefixType, &nextSrcUid, nextAnchorId, nextSuffix)) {
                return;
            }
            if (nextSrcUid == srcUid && !nextAnchorId.empty()) {
                endAnchorId = nextAnchorId;
            }
        }

        //解析资源，得到XMLDoc
        if (spineSrc != lastSpineSrc) {
            MOBIPart *curr = nullptr;
            if (prefixType == 1 && mobi_rawml->flow != nullptr) {
                curr = mobi_rawml->flow;
            } else if (prefixType == 2 && mobi_rawml->markup != nullptr) {
                curr = mobi_rawml->markup;
            } else if (prefixType == 3 && mobi_rawml->resources != nullptr) {
                curr = mobi_rawml->resources;
            } else {
                LOGE("%s: unknown type[%d] or rawml data is null, pass", __func__, srcUid);
                return;
            }

            unsigned char *rawHtml = nullptr;
            size_t rawHtmlSize = 0;
            while (curr != nullptr) {
                MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
                if (curr->size > 0 && file_meta.type == T_HTML && curr->uid == srcUid) {
                    rawHtml = curr->data;
                    rawHtmlSize = curr->size;
                    break;
                }
                curr = curr->next;
            }

            if (rawHtmlSize <= 0 || rawHtml == nullptr) {
                LOGE("%s: failed, unfound chapter page data.", __func__);
                return;
            }

            unsigned char *normalizedHtml = nullptr;
            size_t normalizedHtmlSize = 0;
            TidyDoc tdoc = tidyCreate();
            TidyBuffer output = {0};
            TidyBuffer errbuf = {0};

            //tidy options
            tidyOptSetBool(tdoc, TidyXmlOut, yes); //output xhtml
            tidyOptSetBool(tdoc, TidyQuiet, yes);   //抑制警告
            tidyOptSetInt(tdoc, TidyWrapLen, 0);                //禁用换行
            tidyOptSetValue(tdoc, TidyCharEncoding, "utf8");    //编码集

            tidyParseString(tdoc, std::string(rawHtml, rawHtml + rawHtmlSize).c_str());
            if (tidyCleanAndRepair(tdoc) >= 0 && tidySaveBuffer(tdoc, &output) >= 0) {
                normalizedHtml = output.bp;
                normalizedHtmlSize = output.size;
            } else {
                unsigned char *errInfo = errbuf.bp;
                LOGE("%s:failed %s", __func__, errInfo);
                return;
            }

            if (normalizedHtml == nullptr || normalizedHtmlSize <= 0) {
                LOGE("%s:failed, tidy html failed", __func__);
                normalizedHtmlSize = rawHtmlSize;
                normalizedHtml = rawHtml;
            }
//        LOGD("%s:normalizedHtmlSize=%zu", __func__, normalizedHtmlSize);

            doc.ClearError();
            doc.Clear();
            if (doc.Parse(std::string(normalizedHtml, normalizedHtml + normalizedHtmlSize).c_str(), normalizedHtmlSize) != tinyxml2::XML_SUCCESS) {
                LOGE("%s failed to parse ncx", __func__);
                return;
            }

            lastSpineSrc = spineSrc;
        }

        tinyxml2::XMLElement *root = doc.RootElement();
        if (!root) {
            LOGE("%s failed parse ncx, no root element", __func__);
            return;
        }

        auto body = root->FirstChildElement("body");
        if (!body) {
            LOGE("%s failed parse html, no body element", __func__);
            return;
        }

        auto firstElem = body->FirstChildElement();

        int flagAdd = 0;
        if (anchorId.empty()) {
            flagAdd = 1;
        }

//        std::vector<DocText> docTexts;
        long wordCount = 0;
        if (firstElem != nullptr) {
            countHtmlDoc(env, book_id, mobi_rawml, firstElem, &wordCount, anchorId, endAnchorId, &flagAdd, spineSrc);
//            mockFirstPage(chapter, docTexts);
        }
//        for(auto &text : docTexts) {
//            count += text.text.size();
//        }
        wordCounts.emplace_back(chapter.playOrder, wordCount);
        total += wordCount;
        LOGD("%s: chapter.playOrder[%d], count[%ld]", __func__, chapter.playOrder, wordCount);
    }
    auto end_time = std::chrono::high_resolution_clock::now();
    //    //输出结果统计信息(性能分析)
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: duration = %lld ms", __func__, duration);
}

int mobi_util::countHtmlDoc(JNIEnv *env,
                            long book_id,
                            MOBIRawml *mobi_rawml,
                            tinyxml2::XMLElement *element,
                            long* wordCount,
                            std::string &startAnchorId,
                            std::string &endAnchorId,
                            int *flagAdd,
                            std::string &spineSrcName
//                            std::vector<TagInfo> fatherTags
                            ) {
    tinyxml2::XMLElement *elem = element;
    while (elem != nullptr) {
        if (2 == *flagAdd) {
            break;
        }
//        std::vector<TagInfo> parentTags;
//        if (!fatherTags.empty()) {
//            for (auto &tag: fatherTags) {
//                parentTags.push_back(tag);
//            }
//        }

        std::string name = elem->Name();
        if (name == "p") {
            if (hasChildImg(elem)) {
                auto count = elem->ChildElementCount();
                tinyxml2::XMLElement *child = elem->FirstChildElement();
                if (count > 0 && child != nullptr) {
//                    std::string params = getEleParams(elem, spineSrcName);
//                    if (!params.empty()) {
//                        TagInfo tag;
//                        tag.name = name;;
//                        tag.uuid = generate_uuid();
//                        tag.startPos = 0;
//                        tag.endPos = 0;
//                        tag.params = params;
//                        parentTags.emplace_back(tag);
//                    }

                    countHtmlDoc(env, book_id, mobi_rawml, child, wordCount, startAnchorId, endAnchorId, flagAdd, spineSrcName);
                }
            } else {
                const char *id = elem->Attribute("id");
                std::string aid;
                if (id != nullptr && strlen(id) > 0) {
                    aid = id;
                }
                if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                    *flagAdd = 1;
                } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                    *flagAdd = 2;
                    break;
                }

                std::vector<TagInfo> tagInfos;
//                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);

//                docText.text = text;
//                if (!tagInfos.empty()) {
//                    for (auto &tag: tagInfos) {
//                        docText.tagInfos.push_back(tag);
//                    }
//                }

//                std::string params = getEleParams(elem, spineSrcName);
//                if (!params.empty()) {
//                    TagInfo tag;
//                    tag.name = name;;
//                    tag.uuid = generate_uuid();
//                    tag.startPos = 0;
//                    tag.endPos = utf8Count(text);
//                    tag.params = params;
//                    docText.tagInfos.emplace_back(tag);
//                }
//
//                if (!parentTags.empty()) {
//                    for (auto &tag: parentTags) {
//                        if (tag.endPos == 0) {
//                            tag.endPos = utf8Count(text);
//                        }
//                        docText.tagInfos.push_back(tag);
//                    }
//                }
//
                if (*flagAdd == 1) {
//                    docTexts.push_back(docText);
                    *wordCount += text.size();
                }
            }
        } else if (name == "div" || name == "ul" || name == "ol" || name == "li" || name == "span" || name == "font" || name == "blockquote") {
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

//            const char *divText = elem->GetText();
            if (hasChildText(elem)) {
                std::vector<TagInfo> tagInfos;
//                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);
//                docText.text = text;
//                if (!tagInfos.empty()) {
//                    for (auto &tag: tagInfos) {
//                        docText.tagInfos.push_back(tag);
//                    }
//                }
//
//                std::string params = getEleParams(elem, spineSrcName);
//                if (!params.empty()) {
//                    TagInfo tag;
//                    tag.name = name;;
//                    tag.uuid = generate_uuid();
//                    tag.startPos = 0;
//                    tag.endPos = utf8Count(text);
//                    tag.params = params;
//                    docText.tagInfos.emplace_back(tag);
//                }
//
//                if (!parentTags.empty()) {
//                    for (auto &tag: parentTags) {
//                        if (tag.endPos == 0) {
//                            tag.endPos = utf8Count(text);
//                        }
//                        docText.tagInfos.push_back(tag);
//                    }
//                }
//
                if (*flagAdd == 1) {
//                    docTexts.push_back(docText);
                    *wordCount += text.size();
                }
            } else {
                auto count = elem->ChildElementCount();
                tinyxml2::XMLElement *child = elem->FirstChildElement();
                if (count > 0 && child != nullptr) {
//                    std::string params = getEleParams(elem, spineSrcName);
//                    if (!params.empty()) {
//                        TagInfo tag;
//                        tag.name = name;;
//                        tag.uuid = generate_uuid();
//                        tag.startPos = 0;
//                        tag.endPos = 0;
//                        tag.params = params;
//                        parentTags.emplace_back(tag);
//                    }

                    countHtmlDoc(env, book_id, mobi_rawml, child, wordCount, startAnchorId, endAnchorId, flagAdd, spineSrcName);
                }
            }
        } else if (name == "h1" || name == "h2" || name == "h3" || name == "h4" || name == "h5" || name == "h6" || name == "h7") {
            const char *elemText = elem->GetText();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

            if (elemText != nullptr && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                std::string elemTextStr(elemText, elemText + strlen(elemText));
                cleanStr(elemTextStr);
//                DocText docText{elemTextStr, tagInfos};

//                auto tag = TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", getEleParams(elem, spineSrcName)};
//                docText.tagInfos.push_back(tag);
//                if (!parentTags.empty()) {
//                    for (auto &tag: parentTags) {
//                        if (tag.endPos == 0) {
//                            tag.endPos = utf8Count(docText.text);
//                        }
//                        docText.tagInfos.push_back(tag);
//                    }
//                }

                if (1 == *flagAdd) {
//                    docTexts.push_back(docText);
                    *wordCount += elemTextStr.size();
                }
            } else {
                std::vector<TagInfo> tagInfos;
//                DocText docText{"", tagInfos};
                std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);
//                if (!text.empty() || !tagInfos.empty()) {
//                    docText.text = text;
//                    docText.tagInfos.push_back(TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", getEleParams(elem, spineSrcName)});
//                    if (!tagInfos.empty()) {
//                        for (auto &tag: tagInfos) {
//                            docText.tagInfos.push_back(tag);
//                        }
//                    }
//                    if (!parentTags.empty()) {
//                        for (auto &tag: parentTags) {
//                            if (tag.endPos == 0) {
//                                tag.endPos = utf8Count(docText.text);
//                            }
//                            docText.tagInfos.push_back(tag);
//                        }
//                    }
//                    docTexts.push_back(docText);
//                }
                if (1 == *flagAdd) {
                    *wordCount += text.size();
                }
            }
        } else if (name == "strong" || name == "em" || name == "b" || name == "i") {
            const char *elemText = elem->GetText();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }
            if (elemText != nullptr && utf8Count(elemText) > 0) {
                std::string elemTextStr(elemText, elemText + strlen(elemText));
                cleanStr(elemTextStr);
//                std::vector<TagInfo> tagInfos;
//                DocText docText{elemTextStr, tagInfos};
//                docText.tagInfos.push_back(TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", getEleParams(elem, spineSrcName)});
//                if (!parentTags.empty()) {
//                    for (auto &tag: parentTags) {
//                        if (tag.endPos == 0) {
//                            tag.endPos = utf8Count(docText.text);
//                        }
//                        docText.tagInfos.push_back(tag);
//                    }
//                }
                if (1 == *flagAdd) {
//                    docTexts.push_back(docText);
                    *wordCount += elemTextStr.size();
                }
            }
        } else if (name == "a") {
//            const char *elemText = elem->GetText();
            const char *id = elem->Attribute("id");
            std::string aid;
            if (id != nullptr && strlen(id) > 0) {
                aid = id;
            }
            if (0 == *flagAdd && !startAnchorId.empty() && startAnchorId == aid) {
                *flagAdd = 1;
            } else if (1 == *flagAdd && !endAnchorId.empty() && endAnchorId == aid) {
                *flagAdd = 2;
                break;
            }

//            std::string text;
//            if (elemText != nullptr && strlen(elemText) > 0) {
//                text = elemText;
//            }
            std::vector<TagInfo> tagInfos;
//            DocText docText{"", tagInfos};
            std::string text = processParagraph(elem, tagInfos, startAnchorId, endAnchorId, flagAdd, spineSrcName);
//            docText.text = text;
//            if (!tagInfos.empty()) {
//                for (auto &tag: tagInfos) {
//                    docText.tagInfos.push_back(tag);
//                }
//            }
//
//            std::string params = getEleParams(elem, spineSrcName);
//            docText.tagInfos.push_back(TagInfo{generate_uuid(), aid, name, 0, utf8Count(docText.text), "", params});
//            if (!parentTags.empty()) {
//                for (auto &tag: parentTags) {
//                    if (tag.endPos == 0) {
//                        tag.endPos = utf8Count(docText.text);
//                    }
//                    docText.tagInfos.push_back(tag);
//                }
//            }
//
            if (1 == *flagAdd) {
//                docTexts.push_back(docText);
            *wordCount += text.size();
            }
        } else if (name == "img") {
//            const char *imgSrc = elem->Attribute("src");
//            if (imgSrc != nullptr && utf8Count(imgSrc) > 0) {
//                std::string imgSrcStr = imgSrc;
//                std::string prefix;
//                std::string spineSrc;
//                std::string suffix;
//                std::string anchorId;
//                int prefixType;
//                int srcUid;
//                if (1 == parseSrcName(imgSrcStr, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
////                    LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
////                         __func__, imgSrc, prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);
//                    int width = 0, height = 0;
//                    cacheImage(env, book_id, mobi_rawml, imgSrcStr, prefixType, srcUid, &width, &height);
//                    std::string fullpath = app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id) + separator + imgSrc;
//                    if (width > 0 && height > 0) {
//                        std::vector<TagInfo> tags;
//                        DocText docText{"", tags};
//                        std::string params = "src=" + fullpath + "&width=" + std::to_string(width) + "&height=" + std::to_string(height);
//                        docText.tagInfos.emplace_back(TagInfo{generate_uuid(), "", "img", 0, 0, "", params});
//                        if (!parentTags.empty()) {
//                            for (auto &tag: parentTags) {
//                                if (tag.endPos == 0) {
//                                    tag.endPos = utf8Count(docText.text);
//                                }
//                                docText.tagInfos.push_back(tag);
//                            }
//                        }
//                        if (1 == *flagAdd) {
//                            docTexts.push_back(docText);
//                        }
//                    } else {
//                        LOGE("%s:failed,image[%s] width[%d]height[%d] err", __func__, fullpath.c_str(), width, height);
//                    }
//                }
//            }
        }
        elem = elem->NextSiblingElement();
    }
    return 1;
}