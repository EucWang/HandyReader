//
// Created by MAC on 2025/6/18.
//

#include "epub_util.h"

const std::string container = "META-INF/container.xml";
const std::string mimetype = "mimetype";

/***
 * @param format_str [in/out] 需要格式化的字符串，
 * @return 1 成功，0 失败
 */
int tidy_html(std::string &format_str) {
    std::string output_str;
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

    tidyParseString(tdoc, format_str.c_str());
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
    } else {
        format_str = std::string(normalizedHtml, normalizedHtml + normalizedHtmlSize);
    }
    return 1;
}


int epub_util::epub_init() {

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
    if (1 != zip_ext::read_zip_file(uf, "META-INF/container.xml", container_data)) {
        return 0;
    }
    if (1 != tidy_html(container_data)) {
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
    const char *content_path = rootfile->Attribute("full-path");
    if (content_path == nullptr || strlen(content_path) == 0) {
        LOGE("%s failed content.opf path is null or empty", __func__);
        return 0;
    }
    LOGD("%s:content.opf path is [%s]", __func__, content_path);
    std::string opf_content_data;
    if (1 != zip_ext::read_zip_file(uf, content_path, opf_content_data)) {
        return 0;
    }
    if (1 != tidy_html(opf_content_data)) {
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
    book_date = xml_ext::getText(opfMetadataEle->FirstChildElement("dc:data"));
    book_isbn = xml_ext::getText(
            xml_ext::getChildByNameAndAttr(opfMetadataEle, "dc:identifier", "opf:scheme", "ISBN"));
//    <meta content="cover-image" name="cover"/>
    auto coverEle = xml_ext::getChildByNameAndAttr(opfMetadataEle, "meta", "name", "cover");
    std::string cover_id = xml_ext::getEleAttr(coverEle, "content");
    if (!cover_id.empty()) {
        auto manifestEle = opfRoot->FirstChildElement("manifest");
        auto coverItemEle = xml_ext::getChildByNameAndAttr(manifestEle, "item", "id", cover_id);
        std::string cover_href = xml_ext::getEleAttr(coverItemEle, "href");
        std::string cover_type = xml_ext::getEleAttr(coverItemEle, "media-type");
        std::string ext = file_ext::get_media_type_ext(cover_type);

        if (!cover_href.empty() && !ext.empty()) {
            std::string output_cover_path;
            if (1 == file_ext::get_cover_path(book_title, ext, output_cover_path))  {
                if (1 == zip_ext::write_zip_item_to_file(uf, cover_href, output_cover_path)) {
                    book_coverPath = output_cover_path;
                }
            }
        }
    }

    unzClose(uf);

    return 1;
}

int epub_util::getChapters(/*out*/std::vector<NavPoint> &points) {
    return 1;
}

int epub_util::getChapter(JNIEnv *env, long book_id, const char *path, NavPoint &chapter,
                          std::vector<DocText> &docTexts) {
    return 1;
}

int epub_util::getCss(std::vector<std::string> &cssClasses, std::vector<CssInfo> &cssInfos) {
    return 1;
}

int32_t epub_util::getWordCount(std::vector<std::pair<int32_t, int32_t>> &wordCounts) {

    return 1;
}
