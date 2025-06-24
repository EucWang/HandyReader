//
// Created by MAC on 2025/6/18.
//

#include "epub_util.h"

const std::string epub_zfile_container = "META-INF/container.xml";
const std::string epub_zfile_mimetype = "mimetype";
const std::string epub_zfile_toc_ncx = "toc.ncx";

int epub_util::epub_init() {
    if (book_id == 0L || book_path.empty()) {
        return 0;
    }

    bookzip = unzOpen(book_path.c_str());
    if (bookzip == nullptr) {
        LOGE("%s cannot open file[%s]", __func__, book_path.c_str());
        return 0;
    }

    unz_global_info gi; //获取zip文件中的条目数
    int err = unzGetGlobalInfo(bookzip, &gi);
    if (err != UNZ_OK) {
        LOGE("%s cannot get zip file info", __func__);
        unzClose(bookzip);
        return 0;
    }

    //解析"META-INF/container.xml" ，获得opf路径
    std::string container_data;
    if (1 != zip_ext::read_zip_file(bookzip, epub_zfile_container, container_data)) {
        return 0;
    }
    if (1 != tidyh5_ext::tidy_html(container_data)) {
        return 0;
    }
    tinyxml2::XMLDocument doc;
    doc.ClearError();
    doc.Clear();
    if (doc.Parse(container_data.c_str(), container_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse ncx", __func__);
        return 0;
    }
    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse ncx, no root element", __func__);
        return 0;
    }

    auto rootfiles = root->FirstChildElement("rootfiles");
    if (rootfiles == nullptr) {
        LOGE("%s failed parse html, no rootfiles element", __func__);
        return 0;
    }
    auto rootfile = rootfiles->FirstChildElement("rootfile");
    if (rootfile == nullptr) {
        LOGE("%s failed parse html, no rootfile element", __func__);
        return 0;
    }
    opf_path = xml_ext::getEleAttr(rootfile, "full-path");
    if (opf_path.empty()) {
        LOGE("%s failed content.opf path is null or empty", __func__);
        return 0;
    }

    if (opf_path.find("/") != std::string::npos) {
        std::vector<std::string> paths = split(opf_path, '/');
        ncx_path = paths[0] + "/" + epub_zfile_toc_ncx;
    } else {
        ncx_path = epub_zfile_toc_ncx;
    }

    LOGD("%s:content.opf path is [%s]", __func__, opf_path.c_str());
    std::string opf_content_data;
    if (1 != zip_ext::read_zip_file(bookzip, opf_path, opf_content_data)) {
        return 0;
    }
    if (1 != tidyh5_ext::tidy_html(opf_content_data)) {
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

    auto opfMetadataEle = opfRoot->FirstChildElement("metadata");
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

    auto manifestElem = opfRoot->FirstChildElement("manifest");
    if (manifestElem != nullptr) {
        auto item = manifestElem->FirstChildElement("item");
        while (item != nullptr) {
            std::string href = xml_ext::getEleAttr(item, "href");
            std::string id = xml_ext::getEleAttr(item, "id");
            std::string media_type = xml_ext::getEleAttr(item, "media-type");
            this->manifests.emplace_back(BookManifest{href, id, media_type});

            item = item->NextSiblingElement("item");
        }
    }


    auto spineElem = opfRoot->FirstChildElement("spine");
    if (spineElem != nullptr) {
        auto item = spineElem->FirstChildElement("itemref");
        while (item != nullptr) {
            std::string idref = xml_ext::getEleAttr(item, "idref");
            this->spines.emplace_back(BookSpine{idref});

            item = item->NextSiblingElement("itemref");
        }
    }
    return 1;
}

void epub_util::epub_release() {
    std::lock_guard<std::mutex> lock(m_Mutex2);
    if (initStatus) {
        unzClose(bookzip);
        initStatus = false;
    }
}

int epub_util::parseSrcName(std::string &inputSrc/*in*/,
                            std::string &spineSrc/*out*/,
                            std::string &anchorId/*out*/) {
    if (inputSrc.find('#') != std::string::npos) {
        std::vector<std::string> parts = split(inputSrc, '#');
        if (parts.size() == 2) {
            spineSrc = parts[0];
            anchorId = parts[1];
        }
    } else {
        spineSrc = inputSrc;
    }
    return 1;
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

    unzFile uf = unzOpen(fullpath.c_str());
    if (uf == nullptr) {
        LOGE("%s cannot open file[%s]", __func__, fullpath.c_str());
        return 0;
    }

    unz_global_info gi; //获取zip文件中的条目数
    int err = unzGetGlobalInfo(uf, &gi);
    if (err != UNZ_OK) {
        LOGE("%s cannot get zip file info", __func__);
        unzClose(uf);
        uf = nullptr;
        return 0;
    }

    std::string container_data;
    if (1 != zip_ext::read_zip_file(uf, epub_zfile_container, container_data)) {
        return 0;
    }
    if (1 != tidyh5_ext::tidy_html(container_data)) {
        return 0;
    }
    tinyxml2::XMLDocument doc;
    doc.ClearError();
    doc.Clear();
    if (doc.Parse(container_data.c_str(), container_data.size()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse ncx", __func__);
        return 0;
    }
    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse ncx, no root element", __func__);
        return 0;
    }

    auto rootfiles = root->FirstChildElement("rootfiles");
    if (rootfiles == nullptr) {
        LOGE("%s failed parse html, no rootfiles element", __func__);
        return 0;
    }
    auto rootfile = rootfiles->FirstChildElement("rootfile");
    if (rootfile == nullptr) {
        LOGE("%s failed parse html, no rootfile element", __func__);
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
    if (1 != tidyh5_ext::tidy_html(opf_content_data)) {
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
    auto opfMetadataEle = opfRoot->FirstChildElement("metadata");
    if (opfMetadataEle == nullptr) {
        LOGE("%s opf metadata is null", __func__);
        return 0;
    }

//    std::string &book_review,
//    std::string &book_imprint,
//    std::string &book_copyright,
//    std::string &book_asin,
//    bool &book_isEncrypted
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
//    <meta content="cover-image" name="cover"/>
    std::string cover_id = xml_ext::getEleAttr(xml_ext::getChildByNameAndAttr(opfMetadataEle, "meta", "name", "cover"), "content");
    if (!cover_id.empty()) {
        auto manifestEle = opfRoot->FirstChildElement("manifest");
        auto coverItemEle = xml_ext::getChildByNameAndAttr(manifestEle, "item", "id", cover_id);
        std::string cover_href = xml_ext::getEleAttr(coverItemEle, "href");
        std::string cover_type = xml_ext::getEleAttr(coverItemEle, "media-type");
        std::string ext = file_ext::get_media_type_ext(cover_type);

        if (!cover_href.empty() && !ext.empty()) {
            std::string output_cover_path = file_ext::get_cover_path(book_title, ext);
            if (!output_cover_path.empty()) {
                LOGD("%s: output cover path [%s]", __func__, output_cover_path.c_str());
                if (1 == zip_ext::write_zip_item_to_file(uf, cover_href, output_cover_path)) {
                    book_coverPath = output_cover_path;
                } else {
                    LOGE("%s dump cover to local path failed", __func__);
                }
            } else {
                LOGE("%s: get cover path failed", __func__);
            }
        }
    }

    unzClose(uf);

    return 1;
}

int epub_util::parseOpfData(std::vector<NavPoint> &points) {
    std::vector<std::string> orderedItemSrc;
    int index = 0;
    for (auto &manifest: manifests) {
        if (spines[index].idref == manifest.id && !manifest.href.empty()) {
            orderedItemSrc.push_back(manifest.href);
            index++;
        }
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

    if (orderedItemSrc.size() == 1) {
        isSingleSrc = true;
    } else {
        isSingleSrc = false;
    }

    //全部都在一个资源文件中,这种情况下
    //如果第一个章节的路径中，包含有锚点，则需要手动增加一个从页面0页，防止漏掉内容
    if (orderedItemSrc.size() == 1 && points.size() > 1) {
        std::string &src = points[0].src;
        std::string anchorId;
        if (src.find("#") != std::string::npos) {
            std::vector<std::string> parts = split(src, '#');
            if (parts.size() == 2) {
                anchorId = parts[1];
            }
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


int epub_util::getChapters(/*out*/std::vector<NavPoint> &points) {
    std::lock_guard<std::mutex> lock(m_Mutex);
    if (!initStatus) {
        LOGE("%s:init status failed, so pass", __func__);
        return 0;
    }

    if (!allChapters.empty()) {
        points.insert(points.end(), allChapters.begin(), allChapters.end());
        return 1;
    }

    std::string ncx_data;
    if (1 != zip_ext::read_zip_file(bookzip, ncx_path, ncx_data)) {
        LOGE("%s get [%s] data failed", __func__, ncx_path.c_str());
        return 0;
    }

    if (1 != xml_ext::parseNcxData(ncx_data, points)) {
        LOGE("%s failed, cannot pass ncx", __func__);
        return 0;
    }
    if (1 != parseOpfData(points)) {
        LOGE("%s failed, cannot pass opf", __func__);
        return 0;
    }

    allChapters.clear();
    allChapters.insert(allChapters.end(), points.begin(), points.end());
    return 1;
}

int epub_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter,
                          std::vector<DocText> &docTexts) {

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
            if (nextChapterSpineSrc == spineSrc) {
                endAnchorId = nextChapterAnchorId;
            }
        }
    }

    if (spineSrc != currentSrc) {
        std::string chapter_data;
        if (1 != zip_ext::read_zip_file(bookzip, spineSrc, chapter_data)) {
            LOGE("%s read [%s] failed", __func__, spineSrc.c_str());
            return 0;
        }
        if (1 != tidyh5_ext::tidy_html(chapter_data)) {
            LOGE("%s tidy html %s failed", __func__, spineSrc.c_str());
            return 0;
        }

        doc.ClearError();
        doc.Clear();
        if (doc.Parse(chapter_data.c_str(), chapter_data.size()) != tinyxml2::XML_SUCCESS) {
            LOGE("%s failed to parse %s", __func__, spineSrc.c_str());
            return 0;
        }

        currentSrc = spineSrc;
    }

    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed, no root element", __func__);
        return 0;
    }
    auto body = root->FirstChildElement("body");
    if (!body) {
        LOGE("%s failed, no body element", __func__);
        return 0;
    }
    std::string bodyId = xml_ext::getEleAttr(body, "id");
    auto childEle = body->FirstChildElement();
    int flagAdd = 0;
    if (anchorId.empty()) {
        flagAdd = 1;
    } else {
        if (!bodyId.empty() && bodyId == anchorId) {
            flagAdd = 1;
        } else {
            std::string firstId = xml_ext::getEleAttr(childEle, "id");
            if (!firstId.empty() && firstId == anchorId) {
                flagAdd = 1;
            }
        }

        auto ele = xml_ext::findEleById(childEle, anchorId.c_str());
        if (ele != nullptr) {
            std::string eleName = xml_ext::ele_name(ele);
            if (eleName != "body") {
                childEle = ele;
                flagAdd = 1;
            } else {
                ele = ele->FirstChildElement();
                if (ele != nullptr) {
                    childEle = ele;
                    flagAdd = 1;
                }
            }
        }
    }

    if (childEle != nullptr) {
        std::vector<TagInfo> tags;
//        parseHtmlDoc(env, book_id, childEle, docTexts, anchorId, endAnchorId, &flagAdd, spineSrc, tags);
        xml_ext::parse(book_id, childEle, docTexts, anchorId, endAnchorId, &flagAdd, spineSrc);
        mockFirstPage(chapter, docTexts);
        handle_image(env, docTexts);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: invoke done duration = %lld ms", __func__, duration);
    return 1;
}

void epub_util::handle_image(JNIEnv *env, std::vector<DocText> &docTexts) {
    for (auto &doctext: docTexts) {
        if (!doctext.tagInfos.empty()) {
            auto itag = doctext.tagInfos.begin();
            for(; itag != doctext.tagInfos.end(); ++itag) {
                if ((*itag).name == "img" || (*itag).name == "image") {
                    break;
                }
            }
            if (itag != doctext.tagInfos.end()) {
                TagInfo imgtag = (*itag);
                doctext.tagInfos.erase(itag);
                std::string params = imgtag.params;
                auto kvs = xml_ext::parse_str_params(params);
                std::string imgSrc;
                int width = 0;
                int height = 0;
                for(auto &kv : kvs) {
                    if (kv.first == "src") {
                        imgSrc = kv.second;
                    } else if (kv.first == "width") {
                        width = toInt(kv.second);
                    } else if (kv.first == "height") {
                        width = toInt(kv.second);
                    }
                }
                if (!imgSrc.empty()) {
                    int srcWidth;
                    int srcHeight;
                    if (1 == cache_image(env, imgSrc, &srcWidth, &srcHeight)) {
                        std::string imgPath = file_ext::get_img_path(book_id, imgSrc);
                        if (srcHeight > 0 && srcHeight > 0) {
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
                            doctext.tagInfos.push_back(imgtag);
                        }
                    }
                }
            }
        }
    }
}

int epub_util::parse_css_list() {
    if (isEmptyCss) {
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
    return 1;
}

int epub_util::getCss(std::vector<std::string> &cssClasses, std::vector<CssInfo> &cssInfos) {
    std::lock_guard<std::mutex> lock(m_Mutex2);
    parse_css_list();
    if (cssSrc.empty()) {
        LOGE("%s failed get cssSrc, no css items", __func__);
        return 0;
    }

    future::CSSParser cssParser;

    for (auto &csszip: cssSrc) {
        std::string cssData;
        if (1 != zip_ext::read_zip_file(bookzip, csszip, cssData)) {
            LOGE("%s read css[%s] failed", __func__, csszip.c_str());
            return 0;
        }
        css_ext::query_css(cssData, cssClasses, cssInfos);
    }
    return 1;
}

int32_t epub_util::getWordCount(std::vector<std::pair<int32_t, int32_t>> &wordCounts) {

    return 1;
}


/***
 * 第一章没有内容，由于合并ncx 和opf可能导致的首页没有内容，则需要填充一个默认的内容
 * @param chapter
 * @param docTexts
 */
void epub_util::mockFirstPage(NavPoint &chapter, std::vector<DocText> &docTexts) {
    if (docTexts.empty() && chapter.playOrder == 1) {
        std::string &title = meta_info.title;
        std::string &author = meta_info.author;
        std::string &publisher = meta_info.publisher;
        if (!title.empty()) {
            std::vector<TagInfo> tagInfos;
            tagInfos.push_back(TagInfo{
                    generate_uuid(),
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
                    generate_uuid(),
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
                    generate_uuid(),
                    "",
                    "p",
                    0,
                    publisher.length(),
                    "",
                    "align=center"
            });
            docTexts.emplace_back(DocText{publisher, tagInfos});
        }
    }
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
    int ret = file_ext::checkAndCreateDir(file_ext::get_img_parent_path(book_id), imgSrc);
    if (ret < 0) {
        LOGE("%s:failed, creat dir err", __func__);
        return 0;
    }

    if (ret == 0) {//缓存文件不存在，缓存路径存在或者创建缓存路径成功
        std::string imgzip;
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
        if (imgzip.empty()) {
            LOGE("%s cannot find [%s] in manifest", __func__, imgSrc.c_str());
            return 0;
        }

        if (1 != zip_ext::write_zip_item_to_file(bookzip, imgzip, fullpath)) {
            LOGE("%s write image[%s] to path[%s] failed", __func__, imgzip.c_str(), fullpath.c_str());
            return 0;
        }
    }

    //缓存文件已经存在
    bitmap_ext::getImageOption(env, fullpath.c_str(), width, height);
    return 1;
}
