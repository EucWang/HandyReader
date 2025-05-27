//
// Created by MAC on 2025/4/17.
//

#include "mobi_util.h"
#include <fcntl.h>   // 包含 open() 等文件操作函数
#include <unistd.h>  // 包含 close() 等文件操作函数
#include <iostream>


long mobi_util::last_book_id = 0L;
std::string mobi_util::last_path = "";
MOBIRawml *mobi_util::mobi_rawml = nullptr;
MOBIData *mobi_util::mobi_data = nullptr;
JNIEnv *mobi_util::jniEnv = nullptr;
std::string mobi_util::appFileDir = "";

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

int mobi_util::init(JNIEnv *env, long book_id, const char *path) {
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
    jniEnv = env;
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

int mobi_util::getChapters(JNIEnv *env, long book_id, const char *path, std::vector<NavPoint>& points) {
    if (init(env, book_id, path) != MOBI_SUCCESS) {
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
            if (text != NULL && utf8Count(text) > 0) {
                fullText += text;
                offset += utf8Count(text);
            }
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
int mobi_util::parseSrcName(std::string& src,
                 std::string& prefix,
                 int* prefixType,
                 int* srcId,
                 std::string& anchorId,
                 std::string& suffix) {

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
        if (pos +1 <= src.size()) {
            anchorId = src.substr(pos+1);
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
    } catch(const std::invalid_argument& e) {
        LOGE("%s:failed, uid[%s] is not invalid", __func__, uid.c_str());
        return 0;
    } catch(const std::out_of_range& e) {
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

/***
 * 判断文件是否存在，如果存在返回1；
 * 如果不存在父级路径就创建目录, 如果创建目录失败则返回-1， 否则返回0
 * @param path 文件路径
 * @return
 */
int checkAndCreateDir(const std::string& parentPath, const std::string& fileName) {
    std::string fullPath = parentPath + separator + fileName;
    if (fs::exists(fullPath) && fs::file_size(fullPath) > 0) {
        return true;
    }

    if (!fs::exists(parentPath)) {
        if (fs::create_directories(parentPath)) {
            return 0;
        } else {
            return -1;
        }
    }
    return 0;
}

/***
 * @return 获取系统API版本
 */
int getVersion() {
    char sdk[128] = "0";

    __system_property_get("ro.build.version", sdk);
    int sdk_version = atoi(sdk);
    return sdk_version;
}

int mobi_util::getImageOption(const char* path, int* width, int* height) {
    int outWidth = 0;
    int outHeight = 0;
//    if (getVersion() >= 30) {
//        int fd = open(path, O_RDONLY);
//        if (fd == -1) {
//            close(fd);
//            LOGE("%s:failed,open path[%s] failed", __func__, path);
//            return 0;
//        }
//        AImageDecoder* decoder;
//        int ret = AImageDecoder_createFromFd(fd, &decoder);
//        if (ret != ANDROID_IMAGE_DECODER_SUCCESS) {
//            LOGE("%s:failed,decode image[%s] error", __func__, path);
//            close(fd);
//            return 0;
//        }
//        const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(decoder);
//        if (info != nullptr) {
//            outWidth = AImageDecoderHeaderInfo_getWidth(info);
//            outHeight = AImageDecoderHeaderInfo_getHeight(info);
//            AImageDecoder_delete(decoder);
//        }
//        close(fd);
//    } else {
        if (jniEnv == nullptr) {
            return 0;
        }
        jclass optionClass = jniEnv->FindClass("android/graphics/BitmapFactory$Options");
        if (optionClass == nullptr) {
            return 0;
        }
        jmethodID constructor = jniEnv->GetMethodID(optionClass, "<init>", "()V");
        if (constructor == nullptr) {
            return 0;
        }
        jobject objOption = jniEnv->NewObject(optionClass, constructor);
        if (objOption == nullptr) {
            return 0;
        }
        jfieldID fieldInJustDecodeBounds = jniEnv->GetFieldID(optionClass, "inJustDecodeBounds", "Z");
        jfieldID fieldInSampleSize = jniEnv->GetFieldID(optionClass, "inSampleSize", "I");
        jniEnv->SetBooleanField(objOption, fieldInJustDecodeBounds, true);
        jniEnv->SetIntField(objOption, fieldInSampleSize, 2);

        jclass factoryClass = jniEnv->FindClass("android/graphics/BitmapFactory");
        jmethodID decodeFileMethod = jniEnv->GetStaticMethodID(factoryClass, "decodeFile", "(Ljava/lang/String;Landroid/graphics/BitmapFactory$Options;)Landroid/graphics/Bitmap;");
        jobject objBitmap = jniEnv->CallStaticObjectMethod(factoryClass, decodeFileMethod,
                                       jniEnv->NewStringUTF(path),
                                       objOption);
        jfieldID fieldWidth = jniEnv->GetFieldID(optionClass, "outWidth", "I");
        jfieldID fieldHeight = jniEnv->GetFieldID(optionClass, "outHeight", "I");
        outWidth = jniEnv->GetIntField(objOption,fieldWidth);
        outHeight = jniEnv->GetIntField(objOption, fieldHeight);
//    }
    LOGD("%s:get image[%s] width=%d,height=%d", __func__, path, outWidth, outHeight);
    *width = outWidth;
    *height = outHeight;
    return 1;
}


int mobi_util::cacheImage(std::string& imgSrc, int prefixType, int srcUid, std::vector<DocText>& docTexts){
    //文件路径
    std::string parentPath = appFileDir + separator + "resources" + separator + std::to_string(last_book_id);
    std::string fullpath = parentPath + separator + imgSrc;
    int ret = checkAndCreateDir(parentPath, imgSrc);
    if (ret == 1) { //缓存文件已经存在
        int width = 0, height = 0;
        getImageOption(fullpath.c_str(), &width, &height);
        if (width > 0 && height > 0) {
            std::vector<TagInfo> tags;
            DocText docText{"", tags};
            std::string params = "src=" + fullpath + "&width=" + std::to_string(width) + "&height=" + std::to_string(height);
            docText.tagInfos.emplace_back(TagInfo{generate_uuid(), "", "img", 0, 0, "", params});
            docTexts.push_back(docText);
        } else {
            LOGE("%s:failed,image[%s] width[%d]height[%d] err", __func__, fullpath.c_str(), width, height);
        }
    } else if (ret == 0) {  //缓存文件不存在，缓存路径存在或者创建缓存路径成功
        MOBIPart *curr = NULL;
        if (prefixType == 3 && mobi_rawml->resources != NULL) {
            curr = mobi_rawml->resources;

            unsigned char* rawPic = NULL;
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
                size_t fd = open(fullpath.c_str(), O_CREAT|O_TRUNC|O_RDWR, 0666);
                if (fd == -1) {
                    LOGE("%s:failed,can't create or open img path[%s]", __func__, fullpath.c_str());
                    return 0;
                } else {
                    ret = write(fd, rawPic, rawPicSize);
                    if (ret == -1) {
                        LOGE("%s:failed,can't write to img path[%s]", __func__, fullpath.c_str());
                        return 0;
                    } else {
                        LOGE("%s:write image to path[%s] success", __func__, fullpath.c_str());
//                        AImageDecoder* decoder;
//                        ret = AImageDecoder_createFromBuffer(rawPic, rawPicSize, &decoder);
//                        if (ret == ANDROID_IMAGE_DECODER_SUCCESS) {
//                            const AImageDecoderHeaderInfo* info = AImageDecoder_getHeaderInfo(decoder);
//                            size_t width = AImageDecoderHeaderInfo_getWidth(info);
//                            size_t height = AImageDecoderHeaderInfo_getHeight(info);
//
//                            AImageDecoder_delete(decoder);
//                            if (width > 0 && height > 0) {
//                                std::vector<TagInfo> tags;
//                                DocText docText{"", tags};
//                                std::string params = "src=" + path + "&width=" + std::to_string(width) + "&height=" + std::to_string(height);
//                                docText.tagInfos.emplace_back(TagInfo{generate_uuid(), "", "img", 0, 0, "", params});
//                                docTexts.push_back(docText);
//                            } else {
//                                LOGE("%s:failed,image[%s] width[%d]height[%d] err", __func__, path.c_str(), width, height);
//                            }
//                        } else {
//                            LOGE("%s:failed,decode image[%s] error", __func__, path.c_str());
//                        }
                    }
                    close(fd);
                    if (ret != -1) {
                        int width = 0;
                        int height = 0;
                        getImageOption(fullpath.c_str(), &width, &height);
                        if (width > 0 && height > 0) {
                            std::vector<TagInfo> tags;
                            DocText docText{"", tags};
                            std::string params = "src=" + fullpath + "&width=" + std::to_string(width) + "&height=" + std::to_string(height);
                            docText.tagInfos.emplace_back(TagInfo{generate_uuid(), "", "img", 0, 0, "", params});
                            docTexts.push_back(docText);
                        } else {
                            LOGE("%s:failed,image[%s] width[%d]height[%d] err", __func__, fullpath.c_str(), width, height);
                            return 0;
                        }
                    }
                }
            } else {
                LOGE("%s:failed,rawPicSize[%d] is null or rawPic is null", __func__, rawPicSize);
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

int mobi_util::parseHtmlDoc(tinyxml2::XMLElement *element, std::vector<DocText>& docTexts) {
    tinyxml2::XMLElement* elem = element;
    while(elem != nullptr) {
        std::string name = elem->Name();
        if (name == "div") {
            const char* divText = elem->GetText();
            if (divText != NULL && utf8Count(divText) > 0) {
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
            } else {
                int count = elem->ChildElementCount();
                tinyxml2::XMLElement * child = elem->FirstChildElement();
                if (count > 0 && child != nullptr) {
                    parseHtmlDoc(child, docTexts);
                }
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
        } else if (name == "h1" || name == "h2" || name == "h3" || name == "h4" || name == "h5" || name == "h6" || name == "h7") {
            const char* elemText = elem->GetText();
            if (elemText != NULL && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                DocText docText{elemText, tagInfos};
                docText.tagInfos.push_back(TagInfo{generate_uuid(), "", name, 0, utf8Count(docText.text), "", ""});
                docTexts.push_back(docText);
            }
        } else if (name == "a") {
            const char* elemText = elem->GetText();
            if (elemText != NULL && utf8Count(elemText) > 0) {
                std::vector<TagInfo> tagInfos;
                DocText docText{elemText, tagInfos};
                const char* id = elem->Attribute("id");
                if (id == nullptr) {
                    id = "";
                }
                docText.tagInfos.push_back(TagInfo{generate_uuid(), id, name, 0, utf8Count(docText.text), "", ""});
                docTexts.push_back(docText);
            }
        } else if (name == "img") {
            const char* imgSrc = elem->Attribute("src");
            if (imgSrc != NULL && utf8Count(imgSrc) > 0) {
                std::string imgSrcStr = imgSrc;
                std::string prefix;
                std::string suffix;
                std::string anchorId;
                int prefixType;
                int srcUid;
                if (1 == parseSrcName(imgSrcStr, prefix, &prefixType, &srcUid, anchorId, suffix)){
                    LOGD("%s:getChapter:src[%s] Info[prefix=%s,srcId=%d,anchorId=%s,suffix=%s,prefixType=%d]",
                         __func__, imgSrc, prefix.c_str(), srcUid, anchorId.c_str(), suffix.c_str(), prefixType);
                    cacheImage(imgSrcStr, prefixType, srcUid, docTexts);
                }
            }
        }
        elem = elem->NextSiblingElement();
    }
    return 1;
}

int mobi_util::getChapter(JNIEnv *env, long book_id, const char *path, const char *app_file_dir, NavPoint& chapter, std::vector<DocText> &docTexts) {
    if (init(env, book_id, path) != MOBI_SUCCESS) {
        return 0;
    }
    if (app_file_dir != NULL) {
        appFileDir = app_file_dir;
    } else {
        return 0;
    }
    std::string src = chapter.src;
    std::string prefix;
    std::string suffix;
    std::string anchorId;
    int prefixType;
    int srcUid;
    if (1 != parseSrcName(src, prefix, &prefixType, &srcUid, anchorId, suffix)){
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
        parseHtmlDoc(firstElem, docTexts);
    }

    return 1;
}

