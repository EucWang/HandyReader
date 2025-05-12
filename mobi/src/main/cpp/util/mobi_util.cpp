//
// Created by MAC on 2025/4/17.
//

#include "mobi_util.h"

std::string replaceExtension(const std::string& filePath, const std::string& newExt) {
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

int mobi_util::loadMobi(std::string fullpath,
                        std::string appFileDir,
                        std::string& coverPath,
//                        std::string& epubPath,

                        std::string& title,
                        std::string& author,
                        std::string& contributor,

                        std::string& subject,
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
                        bool& isEncrypted) {

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
    char* meta_title = mobi_meta_get_title(mobi_data);
    char* meta_author = mobi_meta_get_author(mobi_data);
    char* meta_contributor = mobi_meta_get_contributor(mobi_data);

    char* meta_subject = mobi_meta_get_subject(mobi_data);
    char* meta_publisher = mobi_meta_get_publisher(mobi_data);
    char* meta_date = mobi_meta_get_publishdate(mobi_data);

    char* meta_description = mobi_meta_get_description(mobi_data);
    char* meta_review = mobi_meta_get_review(mobi_data);
    char* meta_imprint = mobi_meta_get_imprint(mobi_data);

    char* meta_copyright = mobi_meta_get_copyright(mobi_data);
    char* meta_isbn = mobi_meta_get_isbn(mobi_data);
    char* meta_asin = mobi_meta_get_isbn(mobi_data);

    char* meta_language = mobi_meta_get_language(mobi_data);
    bool meta_isEncrypted = mobi_is_encrypted(mobi_data);

    /* Mobi header */
    char* meta_identifier = nullptr;
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
    if(meta_subject) {
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
    if(meta_imprint) {
        imprint = meta_imprint;
    }
    if(meta_copyright) {
        copyright = meta_copyright;
    }
    if (meta_isbn) {
        isbn = meta_isbn;
    }
    if(meta_asin){
        asin = meta_asin;
    }
    if(meta_language) {
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
    char* targetPath = cover_path;
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
    if(meta_contributor) {
        free(meta_contributor);
    }
    if (meta_subject) {
        free(meta_subject);
    }
    if (meta_publisher) {
        free(meta_publisher);
    }
    if(meta_date) {
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

    if(meta_copyright) {
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