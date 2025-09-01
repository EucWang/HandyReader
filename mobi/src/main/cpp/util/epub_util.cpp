//
// Created by MAC on 2025/6/18.
//

#include "epub_util.h"

const std::string epub_zfile_container = "META-INF/container.xml";
const std::string epub_zfile_mimetype = "mimetype";
const std::string epub_zfile_toc_ncx = "toc.ncx";
const std::string epub_zfile_nav_xhtml = "nav.xhtml";   //epub3 中 可能会用这个文件代替toc.ncx

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
        initStatus = false;
    }
    LOGI("%s:invoke done", __func__);
}


std::string epub_util::cover_to_zip_entity(const std::string &spine_name) {
//    LOGD("%s invoke", __func__);
    std::string ret = spine_name;
    if (spine_name.empty()) {
        return ret;
    }
    if (this->zipEntities.empty()) {
        return ret;
    }

    auto it = std::find_if(zipEntities.begin(), zipEntities.end(), [=](std::string &item){
        return item.find(spine_name) != std::string::npos;
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

    LOGD("%s:content.opf path is [%s]", __func__, opf_path.c_str());
    std::string opf_content_data;
    if (1 != load_zip_entity_data(opf_path, opf_content_data)) {
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

    std::string spine_toc_id = "";
    auto spineElem = opfRoot->FirstChildElement("spine");
    if (spineElem != nullptr) {
        std::string toc_id = xml_ext::getEleAttr(spineElem, "toc");
        if (!toc_id.empty()) {
            spine_toc_id = toc_id;
        }

        auto item = spineElem->FirstChildElement("itemref");
        while (item != nullptr) {
            std::string idref = xml_ext::getEleAttr(item, "idref");
            this->spines.emplace_back(BookSpine{idref});

            item = item->NextSiblingElement("itemref");
        }
    }

    auto manifestElem = opfRoot->FirstChildElement("manifest");
    if (manifestElem != nullptr) {
        auto item = manifestElem->FirstChildElement("item");
        while (item != nullptr) {
            std::string href = xml_ext::getEleAttr(item, "href");
            std::string id = xml_ext::getEleAttr(item, "id");
            std::string media_type = xml_ext::getEleAttr(item, "media-type");
            this->manifests.emplace_back(BookManifest{href, id, media_type});

            if (media_type == xml_ext::MediaTypeTtf || media_type == xml_ext::MediaTypeOtf) {
                LOGD("%s:font assets:id[%s],href[%s],media_type[%s]", __func__, id.c_str(), href.c_str(), media_type.c_str());
            }

            if (!spine_toc_id.empty() && id == spine_toc_id && media_type == xml_ext::MediaTypeNcx) {
                ncx_path = href;
            }

            item = item->NextSiblingElement("item");
        }
    }

    if (ncx_path.empty()) {
        ncx_path = epub_zfile_toc_ncx;
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
    }

    LOGI("%s:invoke done ncx_path[%s]", __func__, ncx_path.c_str());
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
    LOGI("%s:invoke", __func__);

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

    std::vector<std::string> zipfiles = zip_ext::inner_zip_files(uf);

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
        LOGD("%s: cover_id is %s", __func__, cover_id.c_str());
        auto manifestEle = opfRoot->FirstChildElement("manifest");
        auto coverItemEle = xml_ext::getChildByNameAndAttr(manifestEle, "item", "id", cover_id);
        if (coverItemEle != nullptr) {
            std::string cover_href = xml_ext::getEleAttr(coverItemEle, "href");
            std::string cover_type = xml_ext::getEleAttr(coverItemEle, "media-type");
            std::string ext = file_ext::get_media_type_ext(cover_type);


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
                        book_coverPath = output_cover_path;
                    } else {
                        LOGE("%s dump cover to local path failed", __func__);
                    }
                } else {
                    LOGE("%s: get cover path failed", __func__);
                }
            }
        }
    }

    unzClose(uf);

    LOGI("%s:invoke done", __func__);
    return 1;
}

int epub_util::parseOpfData(std::vector<NavPoint> &points) {
    LOGI("%s:invoke", __func__);
    std::vector<std::string> orderedItemSrc;
    for(auto spine : spines) {
        auto it = std::find_if(manifests.begin(), manifests.end(), [=](BookManifest &item){
            return (spine.idref == item.id && item.media_type == xml_ext::MediaTypeHtml && !item.href.empty());
        });
        if (it != manifests.end()) {
            orderedItemSrc.push_back((*it).href);
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
    int index = 0;
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

    //遍历的路径，如果某几个章节对应同一个资源，但是这些章节都不包含这个资源的开头部分
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

    int order = 1;
    for (auto &point: newPoints) {
        point.playOrder = order++;
    }

    points.clear();
    points.insert(points.end(), newPoints.begin(), newPoints.end());
    LOGI("%s:invoke done", __func__);
    return 1;
}

int epub_util::getChapters(/*out*/std::vector<NavPoint> &points) {
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

    //toc.ncx 文件不是必须的。根据 EPUB 3 的规范，toc.ncx 文件已被 nav.xhtml 所取代，因此它不再是 EPUB 的强制要求。
    // 然而，许多出版商为了向前兼容 EPUB 2 的阅读器，仍然会保留 toc.ncx 文件
    std::string ncx_data;
    ncx_path = cover_to_zip_entity(ncx_path);
    if (1 != load_zip_entity_data(ncx_path, ncx_data)) {
        LOGE("%s failed get0 [%s] ncx data failed", __func__, ncx_path.c_str());
        return 0;
    }
    LOGD("%s ncx_path[%s]", __func__, ncx_path.c_str());

    if (!ncx_data.empty()) {
        if (1 != xml_ext::parseNcxData(ncx_data, points)) {
            LOGE("%s failed, cannot pass ncx", __func__);
            return 0;
        }
    }
    if (1 != parseOpfData(points)) {
        LOGE("%s failed, cannot pass opf", __func__);
        return 0;
    }

    allChapters.clear();
    allChapters.insert(allChapters.end(), points.begin(), points.end());

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:invoke done duration = %lld ms", __func__, duration);
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
        std::string chapter_data;
        spineSrc = cover_to_zip_entity(spineSrc);
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }

        LOGD("%s::transform to zip entity src is %s", __func__, spineSrc.c_str());
        if (1 != load_zip_entity_data(spineSrc, chapter_data)) {
            LOGE("%s read [%s] failed", __func__, spineSrc.c_str());
            return 0;
        }
        LOGD("%s::transform done ,chapter_data.size = %zu", __func__, chapter_data.size());
        if (1 != tidyh5_ext::tidy_html(chapter_data)) {
            LOGE("%s tidy html %s failed", __func__, spineSrc.c_str());
            return 0;
        }
        LOGD("%s::tidy_html done ,chapter_data.size = %zu", __func__, chapter_data.size());
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }

        doc.ClearError();
        doc.Clear();
        LOGD("%s::doc clear try to parse", __func__);
        if (doc.Parse(chapter_data.c_str(), chapter_data.size()) != tinyxml2::XML_SUCCESS) {
            LOGE("%s failed to parse %s", __func__, spineSrc.c_str());
            return 0;
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

        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }
        mockFirstPage(chapter, docTexts, meta_info.title, meta_info.author, meta_info.publisher);
        handle_tags(env, docTexts);
    } else {
        LOGE("%s: invoke failed, childEle is null", __func__);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: invoke done duration = %lld ms", __func__, duration);
    return 1;
}

void epub_util::handle_tags(JNIEnv *env, std::vector<DocText> &docTexts) {
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
//    LOGI("%s:invoke", __func__);
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
//    LOGI("%s:invoke done", __func__);
    return 1;
}

int epub_util::getCss(std::vector<std::string> &cssClasses, std::vector<std::string> &cssTags, std::vector<std::string> &cssIds, std::vector<CssInfo> &cssInfos) {
    LOGI("%s:invoke", __func__);
    if (!run_flag) {
        LOGI("%s:invoke failed, run_flag false", __func__);
        return 0;
    }

    auto start_time = std::chrono::high_resolution_clock::now();
    std::lock_guard<std::mutex> lock(m_Mutex2);
    parse_css_list();
    if (cssSrc.empty()) {
        LOGE("%s failed get cssSrc, no css items", __func__);
        return 0;
    }

    future::CSSParser cssParser;

    for (auto &csszip: cssSrc) {
        std::string cssData;
        std::string zipfile = cover_to_zip_entity(csszip);

        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }
        if (1 != load_zip_entity_data(zipfile, cssData)) {
            LOGE("%s read css[%s] failed", __func__, zipfile.c_str());
            return 0;
        }
        css_ext::query_css(cssData, cssClasses, cssTags, cssIds, cssInfos);
    }

    auto end_time = std::chrono::high_resolution_clock::now();
    //    //输出结果统计信息(性能分析)
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGI("%s:invoke done duration = %lld ms", __func__, duration);
    return 1;
}

int32_t epub_util::getWordCount(std::vector<ChapterCount> &wordCounts) {
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

    size_t total = 0;
    tinyxml2::XMLDocument doc2;
    if (!isSingleSrc) {
        std::string lastSpineSrc;
        for (auto item = chapters.begin(); item != chapters.end(); item++) {
            if (!run_flag) {
                LOGI("%s:invoke failed, run_flag false", __func__);
                return 0;
            }
            auto &chapter = (*item);

            std::string spineSrc;
            std::string anchorId;
            parseSrcName(chapter.src, spineSrc, anchorId);

            //下一章节的锚点
            std::string endAnchorId;
            auto nextItem = item + 1;
            if (nextItem != chapters.end()) {
                auto &nextChapter = *nextItem;
                std::string nextChapterSpineSrc;
                std::string nextChapterAnchorId;
                parseSrcName(nextChapter.src, nextChapterSpineSrc, nextChapterAnchorId);

                if (nextChapterSpineSrc == spineSrc) {
                    endAnchorId = nextChapterAnchorId;
                }
            }

            //解析资源，得到XMLDoc
            if (spineSrc != lastSpineSrc) {
                std::string chapter_data;
                spineSrc = cover_to_zip_entity(spineSrc);

                if (!run_flag) {
                    LOGI("%s:invoke failed, run_flag false", __func__);
                    return 0;
                }
                int retry = 3;
                bool flag = false;
                do {
                    if (retry != 3) {
                        LOGD("%s retry[%d] and await 100ms", __func__, retry);
                        std::this_thread::sleep_for(std::chrono::milliseconds(100));
                    }
                    if (1 != load_zip_entity_data(spineSrc, chapter_data)) {
                        LOGE("%s read [%s] failed", __func__, spineSrc.c_str());
                        flag = false;
                        retry--;
                    } else {
                        flag = true;
                    }
                } while(retry > 0 && !flag);
                if (1 != tidyh5_ext::tidy_html(chapter_data)) {
                    LOGE("%s tidy html %s failed", __func__, spineSrc.c_str());
                    return 0;
                }

                doc2.ClearError();
                doc2.Clear();
                if (!run_flag) {
                    LOGI("%s:invoke failed, run_flag false", __func__);
                    return 0;
                }
                if (doc2.Parse(chapter_data.c_str(), chapter_data.size()) != tinyxml2::XML_SUCCESS) {
                    LOGE("%s failed to parse %s", __func__, spineSrc.c_str());
                    return 0;
                }

                lastSpineSrc = spineSrc;
            }

            int flagAdd = 0;
            tinyxml2::XMLElement *childEle = xml_ext::getStartElement(doc2.RootElement(), &flagAdd, anchorId);

            size_t wordCount = 0;
            size_t picCount = 0;
            if (childEle != nullptr) {
                if (!run_flag) {
                    LOGI("%s:invoke failed, run_flag false", __func__);
                    return 0;
                }
                xml_ext::count_words(childEle, anchorId, endAnchorId, &flagAdd, &wordCount, &picCount, &run_flag);
            }
            wordCounts.emplace_back(ChapterCount{chapter.playOrder, wordCount, picCount});
            total += wordCount;
            total += picCount;
            LOGD("%s: chapter.playOrder[%d], count[%zu]", __func__, chapter.playOrder, wordCount);
        }
    } else {
        std::vector<std::string> anchors;
        std::string spineSrc;   //对应的资源文件名

        for (auto &chapter: chapters) {
            std::string spineSrc;
            std::string anchorId;
            parseSrcName(chapter.src, spineSrc, anchorId);

            anchors.push_back(anchorId);
        }
        LOGD("%s:spineSrc=[%s],currentSrc=[%s]", __func__, spineSrc.c_str(), currentSrc.c_str());
        if (spineSrc != currentSrc) {
            std::string chapter_data;
            spineSrc = cover_to_zip_entity(spineSrc);
            if (!run_flag) {
                LOGI("%s:invoke failed, run_flag false", __func__);
                return 0;
            }
            if (1 != load_zip_entity_data(spineSrc, chapter_data)) {
                LOGE("%s read [%s] failed", __func__, spineSrc.c_str());
                return 0;
            }
            if (1 != tidyh5_ext::tidy_html(chapter_data)) {
                LOGE("%s tidy html %s failed", __func__, spineSrc.c_str());
                return 0;
            }

            doc2.ClearError();
            doc2.Clear();
            if (!run_flag) {
                LOGI("%s:invoke failed, run_flag false", __func__);
                return 0;
            }
            if (doc2.Parse(chapter_data.c_str(), chapter_data.size()) != tinyxml2::XML_SUCCESS) {
                LOGE("%s failed to parse %s", __func__, spineSrc.c_str());
                return 0;
            }

            currentSrc = spineSrc;
        }
        tinyxml2::XMLElement *root = doc2.RootElement();
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

        std::vector<std::pair<size_t, size_t>> counts;
        if (!run_flag) {
            LOGI("%s:invoke failed, run_flag false", __func__);
            return 0;
        }
        total = xml_ext::count_words(firstElem, anchors, counts, &run_flag);
        for (int i = 0; i < anchors.size(); ++i) {
            auto &anchor = anchors[i];
            auto count = counts[i];
            int playOrder = chapters[i].playOrder;
            wordCounts.emplace_back(ChapterCount{playOrder, count.first, count.second});
            LOGD("%s:playOrder[%d],anchor=[%s],count=[%ld, %ld]", __func__, playOrder, anchor.c_str(), count.first, count.second);
        }
        LOGD("%s:total=%ld", __func__, total);
    }
    auto end_time = std::chrono::high_resolution_clock::now();
    //    //输出结果统计信息(性能分析)
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
    LOGD("%s: done duration = %lld ms", __func__, duration);
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
