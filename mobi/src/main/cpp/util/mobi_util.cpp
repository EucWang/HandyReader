//
// Created by MAC on 2025/4/17.
//

#include "mobi_util.h"

#include <iostream>
#include <iomanip>
#include <sstream>
#include <random>

long mobi_util::last_book_id = 0L;
std::string mobi_util::last_path = "";
MOBIRawml *mobi_util::mobi_rawml = nullptr;
MOBIData *mobi_util::mobi_data = nullptr;

/****
 * 创建随机的UUID
 * @return
 */
std::string generate_uuid() {
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> dis(0, 15);
    std::uniform_int_distribution<> dis2(8, 11);

    std::stringstream ss;
    int i;
    ss << std::hex;
    for (i = 0; i < 8; i++) {
        ss << dis(gen);
    }
    ss << "-";
    for (i = 0; i < 4; i++) {
        ss << dis(gen);
    }
    ss << "-";
    ss << dis2(gen);
    for (i = 0; i < 3; i++) {
        ss << dis(gen);
    }
    ss << "-";
    ss << dis(gen) % 4 + 8;
    for (i = 0; i < 3; i++) {
        ss << dis(gen);
    }
    ss << "-";
    for (i = 0; i < 12; i++) {
        ss << dis(gen);
    };
    return ss.str();
}

int toInt(std::string value) {
    try {
        return std::stoi(value);
    } catch(const std::invalid_argument& e) {
        LOGE("%s failed, %s, invalide argument: %s", __func__, e.what(), value.c_str());
    } catch(const std::out_of_range& e) {
        LOGE("%s failed, %s, Out of range: %s", __func__, e.what(), value.c_str());
    }
    return 0;
}

/****
 * 替换文件的后缀名
 * @param filePath
 * @param newExt
 * @return
 */
std::string replaceExtension(const std::string &filePath, const std::string &newExt) {
    fs::path path(filePath);

    // 替换后缀名
    if (path.has_extension()) {
        path.replace_extension(newExt);
    } else {
        // 如果没有后缀名，直接追加新后缀
        path += newExt;
    }

    return path.string();
}

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

void mobi_util::getChapter(long book_id, const char *path, const char *app_file_dir, int chapter_index) {

}

