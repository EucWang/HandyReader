//
// Created by MAC on 2025/4/21.
//


#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <ctype.h>
#include <time.h>
#include <sys/stat.h>
#include <errno.h>
/* include libmobi header */
#include "save_epub.h"
// unzip101e headers
//#include <zip.h>
#include "../log.h"
#include "../../unzip101e/zip.h"

#include <regex>
#include <string>



// #define WANT_TIDY_CLEANUP // Nov. 18, 2016 - disconnecting TidyLib
#ifdef WANT_TIDY_CLEANUP
typedef unsigned long ulong;
#   include "tidy.h"
#   include "tidybuffio.h"

    static void TIDY_CALL emptyPutByteFunc(void* sinkData, byte bt)
    {
        // printf("In emptyPutByteFunc()\n");
    }
#endif

/* return codes */
#define ERROR 1
#define ENCRYPTED 10
#define SUCCESS 0

static bool startFileInZip(zipFile zf, const char *name, bool compress) {
    zip_fileinfo zi;
    time_t ltime;
    time(&ltime);
    struct tm *filedate = localtime(&ltime);

    //年月日，时分秒
    zi.tmz_date.tm_sec = filedate->tm_sec;
    zi.tmz_date.tm_min = filedate->tm_min;
    zi.tmz_date.tm_hour = filedate->tm_hour;

    zi.tmz_date.tm_mday = filedate->tm_mday;
    zi.tmz_date.tm_mon = filedate->tm_mon;
    zi.tmz_date.tm_year = filedate->tm_year;

    zi.dosDate = 0;
    zi.internal_fa = 0;
    zi.external_fa = 0;

    if (ZIP_OK != zipOpenNewFileInZip(zf, name, &zi, NULL, 0, NULL, 0, NULL,
                                      compress ? Z_DEFLATED : 0,
                                      compress ? Z_BEST_COMPRESSION : Z_NO_COMPRESSION)) {
        LOGD("zipOpenNewFileInZip error");
        return false;
    }
    return true;
}

/***
 * like strstr() if block is not 0-terminated
 * @param block
 * @param block_len
 * @param target
 * @return
 */
static char *search_block(const char *block, size_t block_len, const char *target) {
    size_t len = strlen(target);
    if (!len || block_len < len) {
        //for 0-length target strstr returns block pointer, i don't care.
        return NULL;
    }
    char *pc;
    pc = (char *) memchr(block, *target, block_len);
    while (pc) {
        if (memcmp(pc, target, len) == 0) {
            return pc;
        }
        pc++;
        block_len -= (pc - block);
        block = pc;

        pc = (char *) memchr(block, *target, block_len);
    }
    return NULL;
}



/**
 * 动态数组记录所有匹配位置
 */
typedef struct {
    size_t *positions; // 匹配的起始位置数组
    size_t count;  // 匹配次数
} MatchResult;

// 查找所有匹配位置
static MatchResult find_all_matches(const char* str, size_t str_len, const char* find, int replace_all) {
    MatchResult result = {NULL, 0};
    size_t find_len = strlen(find);
    if (find_len == 0) return result;

    // 根据 replace_all 预分配空间
    size_t capacity = (replace_all) ? 10 : 1;
    result.positions = (size_t*)malloc(capacity * sizeof(size_t));
    if (!result.positions) return result;

    const char* pos = str;
    while ((pos = strstr(pos, find)) != NULL) {
        size_t offset = pos - str;

        if (offset >= str_len) {
            break;
        }

        // 非 replace_all 模式：仅记录第一个匹配项后退出
        if (!replace_all && result.count >= 1) {
            break;
        }

        // 动态扩容（仅 replace_all 模式需要）
        if (replace_all && result.count >= capacity) {
            capacity *= 2;
            size_t* temp = (size_t*)realloc(result.positions, capacity * sizeof(size_t));
            if (!temp) {
                free(result.positions);
                result.positions = NULL;
                result.count = 0;
                break;
            }
            result.positions = temp;
        }

        result.positions[result.count++] = offset;
        pos += find_len; // 跳过已匹配部分
    }

    return result;
}

static char *replace(const char *original,
                     const size_t original_len,
                     const char *find,
                     const char *replace,
                     int replace_all) {
    if (!original || !find || !replace) return NULL;

    size_t find_len = strlen(find);
    size_t replace_len = strlen(replace);
//    size_t original_len = strlen(original);

    // 查找所有匹配位置
    MatchResult matches = find_all_matches(original, original_len, find, replace_all);
    if (matches.count == 0) {
        free(matches.positions);
        return strdup(original);
    }

    // 计算新字符串总长度
    size_t new_len = original_len + (replace_len - find_len) * matches.count;
    char* new_str = (char*)malloc(new_len + 1);
    if (!new_str) {
        free(matches.positions);
        return NULL;
    }
    memset(new_str, '\0', new_len + 1);

    // 构建新字符串
    size_t last_pos = 0;
    size_t new_str_pos = 0;
    for (size_t i = 0; i < matches.count; i++) {
        size_t match_pos = matches.positions[i];
        // 复制匹配前的部分
        size_t copy_len = match_pos - last_pos;
        memcpy(new_str + new_str_pos, original + last_pos, copy_len);
        new_str_pos += copy_len;
        // 插入替换内容
        memcpy(new_str + new_str_pos, replace, replace_len);
        new_str_pos += replace_len;
        last_pos = match_pos + find_len;
    }
    // 复制剩余部分
    memcpy(new_str + new_str_pos, original + last_pos, original_len - last_pos + 1);

    free(matches.positions);
    return new_str;
}


/****
 * @brief Dump parsed markup files and resources into created folder
 *
 * @param[in] rawml MOBIRawml structure holding parsed records
 * @param[in] epub_fn File to the epub file to be created
 * @return
 *
 * Example sturcture:
 * --ZIP Container--
 * mimetype
 * META-INF/
 *  container.xml
 * OEBPS/
 *  content.opf
 *  chapter1.xhtml
 *  ch1-pic.png
 *  css/
 *      style.css
 *      myfont.otf
 *  toc.ncx
 */
int epub_rawml_parts(const MOBIRawml *rawml, const char *epub_fn) {
    if (rawml == NULL) {
        LOGE("%s:Rawml structure not initialized", __func__);
        return ERROR;
    }
    LOGD("%s:Saving EPUB %s", __func__, epub_fn);

    zipFile zf = zipOpen(epub_fn, APPEND_STATUS_CREATE);
    if (zf == NULL) {
        LOGE("%s:Create EPUB/zip file failed, file name:%s", __func__, epub_fn);
        return ERROR;
    }
    //create regular EPUB structure in zf here...
    bool noError;

    //mimeType
    static const char contents[] = "application/epub+zip";
    noError = startFileInZip(zf, "mimetype", false);
    //must strip ending 0 byte, hence -1
    noError &= (zipWriteInFileInZip(zf, contents, sizeof(contents) - 1) == ZIP_OK);
    noError &= (zipCloseFileInZip(zf) == ZIP_OK);
    if (!noError) {
        LOGE("%s:Could not open file inside EPUB for writing: mimetype", __func__);
        zipClose(zf, NULL);
        return ERROR;
    }

    //META-INF/container.xml
    static const char cont_xml[] =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
            "  <rootfiles>\n"
            "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
            "  </rootfiles>\n"
            "</container>";

    noError = startFileInZip(zf, "META-INF/container.xml", true);
    //again -1 to strip ending 0
    noError &= (zipWriteInFileInZip(zf, cont_xml, sizeof(cont_xml) - 1) == ZIP_OK);
    noError &= (zipCloseFileInZip(zf) == ZIP_OK);

    if (!noError) {
        LOGE("%s:Could not open file inside EPUB for writing:META-INF/container.xml", __func__);
        zipClose(zf, NULL);
        return ERROR;
    }

    //now save all the ebook parts to zf
    char partname[FILENAME_MAX];
    /**
     *linked list of MOBIPart structures in rawml->markup holds main text file
     */
    if (rawml->markup != NULL) {
        MOBIPart *curr = rawml->markup;
        while (curr != NULL) {
            memset(partname, '\0', FILENAME_MAX);
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            sprintf(partname, "OEBPS/part%05zu.%s", curr->uid, file_meta.extension);
            noError = startFileInZip(zf, partname, true);

            //GKochaniak, added - removes erroneous HTML <..<..>..> inside angled brackets
            size_t size = curr->size;
            char *pData = (char *) curr->data;
            char* newData = nullptr;
            if (curr->type == T_HTML) {
                std::regex pattern("(<.[^>]*?)(<.[^<]*?>\\s*</.*?>)(.*?>)");
                char lastChar = pData[size - 1];
                pData[size - 1] = '\0';
                std::match_results<const char *> m;
                const char *data = (const char *) curr->data;
                while (std::regex_search(data, m, pattern) && m.size() == 4) {
                    std::string ss[4];
                    for (int i = 0; i < m.size(); i++) {
                        ss[i] = std::string(m[i].first, m[i].second - m[i].first);
                    }
                    std::string fixed = ss[1] + ss[3] + ss[2];
                    //double check for safe
                    if (fixed.length() == m[0].second - m[0].first) {
                        memcpy((void *) m[0].first, (void *) fixed.c_str(), fixed.length());
                    }
                    data = m[0].second;
                }

//                pData = stringReplace(pData, "<mbp:pagebreak/>", "", 1);
//                if (!data) {
//                    LOGE("%s:Could not replace mbp:pagebreak", __func__);
//                    return ERROR;
//                }
//                size = strlen(pData) + 1;
//                size = strlen(pData);

                pData[size - 1] = lastChar;

                newData = replace(pData, curr->size, "<mbp:pagebreak/>", "", 1);
            }

//            noError &= zipWriteInFileInZip(zf, pData, size) == ZIP_OK;
            noError &= zipWriteInFileInZip(zf, newData, strlen(newData)) == ZIP_OK;
            //GKochaniak, end change
            noError &= zipCloseFileInZip(zf) == ZIP_OK;

            if (newData) {
                free(newData);
            }

            if (!noError) {
                LOGE("%s:Cound not open file inside EPUB for writing:%s", __func__, partname);
                zipClose(zf, NULL);
                return ERROR;
            }

            curr = curr->next;
        }
    }

    /**
     * linked list of MOBIPart structures in rawml->flow holds supplementary text files,
     * eg:
     * .css
     * .svg
     */
    if (rawml->flow != NULL) {
        MOBIPart *curr = rawml->flow;
        //skip raw html file
        curr = curr->next;
        while (curr != NULL) {
            memset(partname, '\0', FILENAME_MAX);
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            sprintf(partname, "OEBPS/flow%05zu.%s", curr->uid, file_meta.extension);
            //optional, get rid of negative text-indent
            if (file_meta.type == T_CSS) {
                char *pc = (char *) curr->data;
                int data_len = curr->size;
                //previously was using here strstr(), but curr->data is not 0-terminated
                while (pc = search_block(pc, data_len, "text-indent:")) {
                    pc += 12; //strlen("text-indent:");
                    //跳过空格
                    while (isspace(*pc) && (pc - (char *) curr->data) < curr->size) {
                        pc++;
                    }
                    if (*pc == '-') {
                        *pc++ = ' ';
                        while (isdigit(*pc)) {
                            *pc++ = ' ';
                        }
                        //now *pc is maybe %, p for px etc.
                        *(--pc) = '0';
                    }
                    data_len = curr->size - (pc - (char *) curr->data);
                }
            }
            noError = startFileInZip(zf, partname, true);
            noError &= zipWriteInFileInZip(zf, curr->data, curr->size) == ZIP_OK;
            noError &= zipCloseFileInZip(zf) == ZIP_OK;
            if (!noError) {
                LOGE("%s:Could not open file inside EPUB for writing:%s", __func__, partname);
                zipClose(zf, NULL);
                return ERROR;
            }
            curr = curr->next;
        }
    }

    /***
     * linked list of MOBIPart structures in rawml->resources holds binary files
     * eg:
     * jpg, gif, png, bml, font, audio, video
     */
    if (rawml->resources != NULL) {
        MOBIPart *curr = rawml->resources;
        while (curr != NULL) {
            memset(partname, '\0', FILENAME_MAX);
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0) {
                MOBIFiletype type = file_meta.type;
                if (type == T_NCX) {
                    sprintf(partname, "OEBPS/toc.%s", file_meta.extension);
                } else if (type == T_OPF) {
                    sprintf(partname, "OEBPS/content.%s", file_meta.extension);
                } else {
                    sprintf(partname, "OEBPS/resource%05zu.%s", curr->uid, file_meta.extension);
                }
                bool compress = !(type == T_JPG || type == T_GIF || type == T_PNG || type == T_MP3 || type == T_MPG);
                noError = startFileInZip(zf, partname, compress);
                noError &= zipWriteInFileInZip(zf, curr->data, curr->size) == ZIP_OK;
                noError &= zipCloseFileInZip(zf) == ZIP_OK;
                if (!noError) {
                    LOGE("%s:Could not open file inside EPUB for writing:%s", __func__, partname);
                    zipClose(zf, NULL);
                    return ERROR;
                }
            }
            curr = curr->next;
        }
    }

    zipClose(zf, NULL);
    return SUCCESS;
}

/****
 * @brief loads Rawml data of Mobi
 *
 * @param [in] m    MOBIData initialized with *m = mobi_init();
 * @param [in] mobiFn : Mobi file name
 * @param pid  Device ID for description, default NULL
 * @param parse_kf7_opt - true if KF7 part of hybrid KF7/KF8 file should be parsed, default false
 * @return
 */
// TODO: Compare with Bartek's latest souces... Error codes?
MOBIRawml *loadMobiRawml(MOBIData *m,
                         const char *mobiFn,
                         const char *pid,
                         bool parse_kf7_opt) {
    MOBI_RET mobi_ret;
    /* Initialize main MOBIData structure */

    /* By default loader will parse KF8 part of hybrid KF7/KF8 file */
    if (parse_kf7_opt) {
        /* Force it to parse KF7 part */
        mobi_parse_kf7(m);
    }
    errno = 0;
    FILE *file = fopen(mobiFn, "rb");
    if (file == NULL) {
        int errsv = errno;
        printf("Error opening file: %s (%s)\n", mobiFn, strerror(errsv));
        return NULL;
    }
    /* MOBIData structure will be filled with loaded document data and metadata */
    mobi_ret = mobi_load_file(m, file);
    fclose(file);
    file = NULL;
    /* Try to print basic metadata, even if further loading failed */
    /* In case of some unsupported formats it may still print some useful info */
    // print_meta(m);
    if (mobi_ret != MOBI_SUCCESS) {
        printf("Error while loading document (%i)\n", mobi_ret);
        return NULL;
    }
    /* Try to print EXTH metadata */
    // print_exth(m);
#ifdef USE_ENCRYPTION
    if (pid != NULL) {
        /* Try to set key for decompression */
        if (m->rh && m->rh->encryption_type == 0) {
            printf("\nDocument is not encrypted, ignoring PID\n");
        }
        else if (m->rh && m->rh->encryption_type == 1) {
            printf("\nEncryption type 1, ignoring PID\n");
        }
        else {
            printf("\nVerifying PID... ");
            mobi_ret = mobi_drm_setkey(m, pid);
            if (mobi_ret != MOBI_SUCCESS) {
                printf("failed (%i)\n", mobi_ret);
                return NULL;
            }
            //printf("ok\n");
        }
    }
#endif

    // printf("\nReconstructing source resources...\n");
    /* Initialize MOBIRawml structure */
    /* This structure will be filled with parsed records data */
    MOBIRawml *rawml = mobi_init_rawml(m);
    if (rawml == NULL) {
        printf("Memory allocation failed\n");
        return NULL;
    }

    /* Parse rawml text and other data held in MOBIData structure into MOBIRawml structure */
    mobi_ret = mobi_parse_rawml(rawml, m);
    if (mobi_ret != MOBI_SUCCESS) {
        printf("Parsing rawml failed (%i)\n", mobi_ret);
        mobi_free_rawml(rawml);
        return NULL;
    }

    return rawml;
}


extern "C" int convertMobiToEpub(const char *mobiFn,
                                 const char *epubFn,
                                 const char *pid,
                                 bool parse_kf7_opt) {

    MOBIData* m = mobi_init();
    if (m == NULL) {
        LOGE("%s:Memory allocation failed.", __func__);
        return false;
    }

    MOBIRawml* rawml = loadMobiRawml(m, mobiFn, pid, parse_kf7_opt);

    if (m->rh != NULL && m->rh->encryption_type != 0) {
        mobi_free(m);
        mobi_free_rawml(rawml);
        return ENCRYPTED;
    }

    if (rawml == NULL) {
        mobi_free(m);
        return ERROR;
    }
    int ret = SUCCESS;
    if (rawml->flow->type == T_PDF) {
        LOGE("%s:This is Replica Print ebook(azw4), got PDF resource", __func__);
#if defined(_DEBUG) && defined(WIN32)
        int len = strlen(epubFn) + 16;
        char* pdfFn = (char*) malloc(len);
        strncpy(pdfFn, epubFn, len);
        for(char *pc = pdfFn + strlen(pdfFn); pc > pdfFn; pc--) {
            if (*pc == '.') {
                *pc = 0;
                break;
            }
        }
        strncat(pdfFn, ".pdf", 5);
        FILE* fp = fopen(pdfFn, "wb");
        free(pdfFn);
        if (fp != NULL) {
            char *pc = (char*)rawml->flow->data;
            fwrite(rawml->flow->data, 1, rawml->flow->size, fp);
            char* s = "\n%HyperionicsAvarOrg: c:/tmp/TestFileName.pdf\n";
            fwrite(s, 1, strlen(s), fp);
            fclose(fp);
            ret = 101;
        }
#endif
        if (ret != 101) {
            ret =  ERROR;
        }
    } else {
        //save parts to files
        ret = epub_rawml_parts(rawml, epubFn);
        if (ret != SUCCESS) {
            LOGE("%s:Dumping parts to EPUB failed.", __func__);
        }
    }

    mobi_free(m);
    mobi_free_rawml(rawml);
    return ret;
}