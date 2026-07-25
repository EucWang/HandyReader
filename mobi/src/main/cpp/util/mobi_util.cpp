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

int mobi_util::load_entity_data(const std::string &src_name, std::string &output_data) {
    if (!run_flag) {
        return 0;
    }
    std::lock_guard<std::mutex> lock(m_Mutex4);

    std::string chapterSrc = src_name;
//    LOGD("%s:chapterSrc=%s", __func__, chapterSrc.c_str());
    std::string &src = chapterSrc;
    std::string prefix;
    std::string spineSrc;
    std::string suffix;
    std::string anchorId;
    int prefixType;
    int srcUid;
    if (1 != innerParseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
        return 0;
    }

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

    unsigned char *raw_data = nullptr;
    size_t raw_data_size = 0;
    while (curr != nullptr) {
        MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
        if (curr->size > 0 && file_meta.type == T_HTML && curr->uid == srcUid) {
            raw_data = curr->data;
            raw_data_size = curr->size;
            break;
        }
        curr = curr->next;
    }

    if (raw_data_size <= 0 || raw_data == nullptr) {
        LOGE("%s: failed, unfound chapter page data.", __func__);
        return 0;
    }
    output_data = std::string(raw_data, raw_data + raw_data_size);
    return 1;
}


void parseNavPoints(tinyxml2::XMLElement *firstNavPoint, std::vector<NavPoint> &vectors, const char *parentId) {
    for (tinyxml2::XMLElement *navPoint = firstNavPoint; navPoint; navPoint = navPoint->NextSiblingElement("navPoint")) {
        std::string id = xml_ext::getEleAttr(navPoint, "id");
        std::string playOrder = xml_ext::getEleAttr(navPoint, "playOrder");

        std::string label = "";
        tinyxml2::XMLElement* navLabel = navPoint->FirstChildElement("navLabel");
        if (navLabel != nullptr) {
            tinyxml2::XMLElement* labelText = navLabel->FirstChildElement("text");
            label = xml_ext::getText(labelText);
        }

        std::string src = "";
        tinyxml2::XMLElement* contentEle = navPoint->FirstChildElement("content");
        std::string srcAttr = xml_ext::getEleAttr(contentEle, "src");
        src = srcAttr;

        NavPoint nav;
        nav.id = id;
        nav.playOrder = string_ext::toInt(playOrder);
        nav.text = label;
        nav.src = src;
        nav.parentId = parentId;
        vectors.push_back(nav);

        if (navPoint->ChildElementCount("navPoint") > 0) {
            parseNavPoints(navPoint->FirstChildElement("navPoint"), vectors, id.c_str());
        }
    }
}

int mobi_util::parseOpfData(const char *opf_data, size_t opf_data_size, std::vector<NavPoint> &points) {
    tinyxml2::XMLDocument opfDoc;
    if (opfDoc.Parse(std::string(opf_data, opf_data + opf_data_size).c_str(), opf_data_size) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse opf", __func__);
        return 0;
    }

    tinyxml2::XMLElement *root = opfDoc.RootElement();
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

        if (id != nullptr &&
            strlen(id) > 0 &&
            index < itemrefs.size() &&
            itemrefs[index] == id &&
            href != nullptr &&
            strlen(href) > 0) {
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
        if (points.empty()) {
            for (int i = startOpfIndex; i < orderedItemSrc.size(); i++) {
                NavPoint point;
                point.src = orderedItemSrc[i];
                point.text = "";
                point.id = string_ext::generate_uuid();
                point.parentId = "";
                newPoints.push_back(point);
            }
        } else {
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
                    point.id = string_ext::generate_uuid();
                    point.parentId = "";
                    newPoints.push_back(point);
                }
            }
        }
    } else if (index < points.size() && startOpfIndex >= orderedItemSrc.size()) {
        for (int i = index; i < points.size(); i++) {
            newPoints.push_back(points[i]);
        }
    }

    if (orderedItemSrc.size() == 1) {
        isSingleSrc = true;
    } else {
        isSingleSrc = false;
    }

    if (orderedItemSrc.size() == 1 && points.size() > 1) { //全部都在一个资源文件中
        std::string &src = points[0].src;
        std::string prefix;
        std::string spineSrc;
        std::string suffix;
        std::string anchorId;
        int prefixType;
        int srcUid;
        if (1 != innerParseSrcName(src, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix)) {
            return 0;
        }
        if (!anchorId.empty()) { //第一章，不是从资源最开始位置开始的
            NavPoint point;
            point.src = orderedItemSrc[0];
            point.text = "";
            point.id = string_ext::generate_uuid();
            point.parentId = "";
            newPoints.insert(newPoints.begin(), point);
        }
    }

    if (!newPoints.empty()) {
        for (int i = 0; i < newPoints.size(); ++i) {
            auto &point = newPoints[i];
            if (point.src.find("#") != std::string::npos) {
                std::string cur_src;
                std::vector<std::string> parts = string_ext::split(point.src, '#');
                if (parts.size() == 2) {
                    cur_src = parts[0];
                }
                std::string pre_src;
                if (i > 0) {
                    auto &pre_point = newPoints[i - 1];
                    if (pre_point.src.find("#") != std::string::npos) {
                        std::vector<std::string> pre_parts = string_ext::split(pre_point.src, '#');
                        if (pre_parts.size() == 2) {
                            pre_src = pre_parts[0];
                        }
                    } else {
                        pre_src = pre_point.src;
                    }
                }
                if (pre_src != cur_src) {
                    point.src = cur_src;
                }
            }
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

    fseek(file, 0, SEEK_END);
    long file_size = ftell(file);
    fseek(file, 0, SEEK_SET);

    char pdb_name[33] = {0};
    char pdb_type[5] = {0};
    size_t pdb_read = fread(pdb_name, 1, 32, file);
    fseek(file, 60, SEEK_SET);
    fread(pdb_type, 1, 4, file);
    fseek(file, 76, SEEK_SET);
    unsigned char rec_count_buf[2] = {0};
    fread(rec_count_buf, 1, 2, file);
    uint16_t rec_count = ((uint16_t)rec_count_buf[0] << 8) | rec_count_buf[1];
    fseek(file, 0, SEEK_SET);

    if (pdb_read >= 32) {
        LOGI("%s PDB pre-check: name=[%s], type=[%s], rec_count=%u, file_size=%ld",
             __func__, pdb_name, pdb_type, rec_count, file_size);
    } else {
        LOGW("%s PDB pre-check: short read (%zu bytes), file_size=%ld",
             __func__, pdb_read, file_size);
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
int mobi_util::innerParseSrcName(std::string &src,
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
        LOGE("%s:src[%s] is not html,empty srcTypeName", __func__, src.c_str());
        return 0;
    }

    if (string_ext::startWith(srcTypeName, flow)) {
        type = 1;
        if (flow.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(flow.size() + 1);
        }
        prefix = flow;
    } else if (string_ext::startWith(srcTypeName, part)) {
        type = 2;
        if (part.size() + 1 < srcTypeName.size()) {
            uid = srcTypeName.substr(part.size() + 1);
        }
        prefix = part;
    } else if (string_ext::startWith(srcTypeName, resource)) {
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

int mobi_util::cache_image(JNIEnv *env, std::string &imgSrc, int prefixType, int srcUid, int *width, int *height) {
    //文件路径
    std::string parentPath = app_ext::appFileDir + separator + "resources" + separator + std::to_string(book_id);
    std::string fullpath = parentPath + separator + file_ext::handle_filename(imgSrc);
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

int mobi_util::getChapter(JNIEnv *env,
                          long book_id,
                          const char *path,
                          NavPoint &chapter,
                          std::vector<DocText> &docTexts) {
    auto start_time = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> lock(m_Mutex);
    LOGD("%s invoke,playOrder[%d],src[%s]", __func__, chapter.playOrder, chapter.src.c_str());
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }
    if (app_ext::appFileDir.empty()) {
        LOGE("%s:failed, appFileDir is empty so pass", __func__);
        return 0;
    }
    std::string chapterSrc = chapter.src;
    std::string spineSrc;
    std::string anchorId;
    parseSrcName(chapterSrc, spineSrc, anchorId);
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
            std::string nextSpineSrc;
            std::string nextAnchorId;

            parseSrcName(nextChapter.src, nextSpineSrc, nextAnchorId);
            if (nextSpineSrc == spineSrc && !nextAnchorId.empty()) {
                endAnchorId = nextAnchorId;
                LOGD("%s:startAnchorId=%s,endAnchorId=%s", __func__, anchorId.c_str(), endAnchorId.c_str());
            }
        }
    }

    std::vector<CssInfo> cssInfos;
    if (spineSrc != currentSrc) {
        std::string chapter_data;
        if (1 != load_entity_data(spineSrc, chapter_data)) {
            LOGE("%s load chapter data failed [%s]", __func__, spineSrc.c_str());
            return 0;
        }
        LOGD("%s::transform done ,chapter_data.size = %zu", __func__, chapter_data.size());
        std::string page_css_style;
        if (1 != tidyh5_ext::tidy_html_with_css(chapter_data, page_css_style)) {
            LOGE("%s tidy html %s failed", __func__, spineSrc.c_str());
            return 0;
        }
        LOGD("%s::tidy_html done ,chapter_data.size = %zu", __func__, chapter_data.size());
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }

        // parse inline CSS
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

        // parse external CSS: extract <link> href, match in cssSrc, load matched CSS files from mobi_rawml
        if (1 == parseCssSrcList() && !cssSrc.empty()) {
            tinyxml2::XMLElement *extRoot = doc.RootElement();
            if (extRoot) {
                tinyxml2::XMLElement *headEle = extRoot->FirstChildElement("head");
                if (headEle) {
                    std::vector<std::string> loadedCssSrcs;
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
                                            bool alreadyLoaded = false;
                                            for (auto &loaded : loadedCssSrcs) {
                                                if (loaded == cssHref) {
                                                    alreadyLoaded = true;
                                                    break;
                                                }
                                            }
                                            if (alreadyLoaded) { break; }

                                            std::string cssSrcName = cssHref;
                                            std::string prefix;
                                            std::string cssSpineSrc;
                                            std::string cssSuffix;
                                            std::string cssAnchorId;
                                            int cssPrefixType;
                                            int cssSrcUid;
                                            if (1 == innerParseSrcName(cssSrcName, prefix, cssSpineSrc, &cssPrefixType, &cssSrcUid, cssAnchorId, cssSuffix)) {
                                                MOBIPart *curr = nullptr;
                                                if (cssPrefixType == 1 && mobi_rawml->flow != nullptr) {
                                                    curr = mobi_rawml->flow;
                                                } else if (cssPrefixType == 2 && mobi_rawml->markup != nullptr) {
                                                    curr = mobi_rawml->markup;
                                                } else if (cssPrefixType == 3 && mobi_rawml->resources != nullptr) {
                                                    curr = mobi_rawml->resources;
                                                }
                                                if (curr != nullptr) {
                                                    unsigned char *rawCss = nullptr;
                                                    size_t rawCssSize = 0;
                                                    while (curr != nullptr) {
                                                        MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
                                                        if (curr->size > 0 && file_meta.type == T_CSS && curr->uid == cssSrcUid) {
                                                            rawCss = curr->data;
                                                            rawCssSize = curr->size;
                                                            break;
                                                        }
                                                        curr = curr->next;
                                                    }
                                                    if (rawCss != nullptr && rawCssSize > 0) {
                                                        std::string cssData(rawCss, rawCss + rawCssSize);
                                                        css_ext::parse_css(cssData, cssInfos);
                                                        loadedCssSrcs.push_back(cssHref);
                                                        LOGD("%s: loaded external css [%s]", __func__, cssHref.c_str());
                                                    } else {
                                                        LOGE("%s: rawCss is null for [%s]", __func__, cssHref.c_str());
                                                    }
                                                }
                                            } else {
                                                LOGE("%s: innerParseSrcName failed for cssHref[%s]", __func__, cssHref.c_str());
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
        // Cache the CSS with the document it was parsed from: the branch above is skipped when the
        // same spine document is requested twice in a row, and cssInfos would then stay empty.
        currentCssInfos = cssInfos;
    } else {
        // Same spine document as the previous call — `doc` is still the parsed one, so reuse its
        // CSS. Without this the chapter gets paginated with no publisher stylesheet at all, making
        // the rendered font size depend on the order in which chapters happen to be loaded.
        cssInfos = currentCssInfos;
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
        std::string titleStr;
        char *title = mobi_meta_get_title(mobi_data);
        if (title != nullptr) {
            titleStr = title;
        }
        std::string authorStr;
        char *author = mobi_meta_get_author(mobi_data);
        if (author != nullptr) {
            authorStr = author;
        }
        std::string publisherStr;
        char *publisher = mobi_meta_get_publisher(mobi_data);
        if (publisher != nullptr) {
            publisherStr = publisher;
        }
        mockFirstPage(chapter, docTexts, titleStr, authorStr, publisherStr);
        handle_tags(env, docTexts, cssInfos);
    }
    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: invoke done duration = %lld ms", __func__, duration);
    return 1;
}

void mobi_util::handle_tags(JNIEnv *env, std::vector<DocText> &docTexts, std::vector<CssInfo> &cssInfos) {
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

                        std::string imgSrcStr = imgSrc;
                        std::string prefix;
                        std::string spineSrc;
                        std::string suffix;
                        std::string anchorId;
                        int prefixType;
                        int srcUid;
                        innerParseSrcName(imgSrcStr, prefix, spineSrc, &prefixType, &srcUid, anchorId, suffix);
                        if (1 == cache_image(env, imgSrc, prefixType, srcUid, &srcWidth, &srcHeight)) {
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

int32_t mobi_util::getWordCount(std::vector<ChapterCount> &wordCounts) {
    LOGD("%s invoke", __func__);
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
    std::vector<ChapterSrcInfo> srcInfos(chapters.size());
    for (size_t i = 0; i < chapters.size(); i++) {
        std::string src = chapters[i].src;
        parseSrcName(src, srcInfos[i].spineSrc, srcInfos[i].anchorId);
        srcInfos[i].playOrder = chapters[i].playOrder;
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

    size_t total = 0;
    std::string rawHtml;
    for (auto &group : groups) {
        if (1 != load_entity_data(group.spineSrc, rawHtml)) {
            LOGE("%s: load_entity_data failed for [%s]", __func__, group.spineSrc.c_str());
            return 0;
        }
        std::vector<std::pair<size_t, size_t>> counts;
        string_ext::count_text_pic_batch(rawHtml, group.anchors, counts);
        for (size_t j = 0; j < counts.size(); j++) {
            wordCounts.emplace_back(ChapterCount{group.orders[j], counts[j].first, counts[j].second});
            total += counts[j].first + counts[j].second;
            LOGD("%s: playOrder[%d], anchor=[%s], charCount[%zu], picCount[%zu]",
                 __func__, group.orders[j], group.anchors[j].c_str(),
                 counts[j].first, counts[j].second);
        }
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: duration = %lld ms, total = %zu", __func__, duration, total);
    return total;
}

int mobi_util::parseDocDom(int prefixType, int srcUid) {
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
    std::string container_data = std::string{rawHtml, rawHtml + rawHtmlSize};
    if (1 != tidyh5_ext::tidy_html(container_data)) {
        return 0;
    }

    doc.ClearError();
    doc.Clear();
    if (doc.Parse(container_data.c_str(), container_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse ncx", __func__);
        return 0;
    }
    return 1;
}