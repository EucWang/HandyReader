/** @file mobitool.c
 *
 * @brief mobitool
 *
 * @example mobitool.c
 * Program for testing libmobi library
 *
 * Copyright (c) 2020 Bartek Fabiszewski
 * http://www.fabiszewski.net
 *
 * Licensed under LGPL, either version 3, or any later.
 * See <http://www.gnu.org/licenses/>
 */
#include "mobitool.h"
#ifdef USE_ENCRYPTION
bool setpid_opt = false;
bool setserial_opt = false;
#endif

/* options values */
#ifdef USE_ENCRYPTION
char *pid = NULL;
char *serial = NULL;
#endif


/* command line options */
bool dump_cover_opt = false;
 bool dump_rawml_opt = false;
 bool create_epub_opt = false;
 bool print_extended_meta_opt = false;
 bool print_rec_meta_opt = false;
 bool dump_rec_opt = false;
 bool parse_kf7_opt = false;
 bool dump_parts_opt = false;
 bool print_rusage_opt = false;
 bool extract_source_opt = false;
 bool split_opt = false;


/**
 @brief Print all loaded headers meta information
 @param[in] m MOBIData structure
 */
void print_meta(const MOBIData *m) {
    /* Full name stored at offset given in MOBI header */
    if (m->mh && m->mh->full_name) {
        char full_name[FULLNAME_MAX + 1];
        if (mobi_get_fullname(m, full_name, FULLNAME_MAX) == MOBI_SUCCESS) {
            LOGD("\nFull name: %s\n", full_name);
        }
    }
    /* Palm database header */
    if (m->ph) {
        LOGD("\nPalm doc header:\n");
        LOGD("name: %s\n", m->ph->name);
        LOGD("attributes: %hu\n", m->ph->attributes);
        LOGD("version: %hu\n", m->ph->version);
        struct tm * timeinfo = mobi_pdbtime_to_time(m->ph->ctime);
        LOGD("ctime: %s", asctime(timeinfo));
        timeinfo = mobi_pdbtime_to_time(m->ph->mtime);
        LOGD("mtime: %s", asctime(timeinfo));
        timeinfo = mobi_pdbtime_to_time(m->ph->btime);
        LOGD("btime: %s", asctime(timeinfo));
        LOGD("mod_num: %u\n", m->ph->mod_num);
        LOGD("appinfo_offset: %u\n", m->ph->appinfo_offset);
        LOGD("sortinfo_offset: %u\n", m->ph->sortinfo_offset);
        LOGD("type: %s\n", m->ph->type);
        LOGD("creator: %s\n", m->ph->creator);
        LOGD("uid: %u\n", m->ph->uid);
        LOGD("next_rec: %u\n", m->ph->next_rec);
        LOGD("rec_count: %u\n", m->ph->rec_count);
    }
    /* Record 0 header */
    if (m->rh) {
        LOGD("\nRecord 0 header:\n");
        LOGD("compression type: %u\n", m->rh->compression_type);
        LOGD("text length: %u\n", m->rh->text_length);
        LOGD("text record count: %u\n", m->rh->text_record_count);
        LOGD("text record size: %u\n", m->rh->text_record_size);
        LOGD("encryption type: %u\n", m->rh->encryption_type);
        LOGD("unknown: %u\n", m->rh->unknown1);
    }
    /* Mobi header */
    if (m->mh) {
        LOGD("\nMOBI header:\n");
        LOGD("identifier: %s\n", m->mh->mobi_magic);
        if (m->mh->header_length) { LOGD("header length: %u\n", *m->mh->header_length); }
        if (m->mh->mobi_type) { LOGD("mobi type: %u\n", *m->mh->mobi_type); }
        if (m->mh->text_encoding) { LOGD("text encoding: %u\n", *m->mh->text_encoding); }
        if (m->mh->uid) { LOGD("unique id: %u\n", *m->mh->uid); }
        if (m->mh->version) { LOGD("file version: %u\n", *m->mh->version); }
        if (m->mh->orth_index) { LOGD("orth index: %u\n", *m->mh->orth_index); }
        if (m->mh->infl_index) { LOGD("infl index: %u\n", *m->mh->infl_index); }
        if (m->mh->names_index) { LOGD("names index: %u\n", *m->mh->names_index); }
        if (m->mh->keys_index) { LOGD("keys index: %u\n", *m->mh->keys_index); }
        if (m->mh->extra0_index) { LOGD("extra0 index: %u\n", *m->mh->extra0_index); }
        if (m->mh->extra1_index) { LOGD("extra1 index: %u\n", *m->mh->extra1_index); }
        if (m->mh->extra2_index) { LOGD("extra2 index: %u\n", *m->mh->extra2_index); }
        if (m->mh->extra3_index) { LOGD("extra3 index: %u\n", *m->mh->extra3_index); }
        if (m->mh->extra4_index) { LOGD("extra4 index: %u\n", *m->mh->extra4_index); }
        if (m->mh->extra5_index) { LOGD("extra5 index: %u\n", *m->mh->extra5_index); }
        if (m->mh->non_text_index) { LOGD("non text index: %u\n", *m->mh->non_text_index); }
        if (m->mh->full_name_offset) { LOGD("full name offset: %u\n", *m->mh->full_name_offset); }
        if (m->mh->full_name_length) { LOGD("full name length: %u\n", *m->mh->full_name_length); }
        if (m->mh->locale) {
            const char *locale_string = mobi_get_locale_string(*m->mh->locale);
            if (locale_string) {
                LOGD("locale: %s (%u)\n", locale_string, *m->mh->locale);
            } else {
                LOGD("locale: unknown (%u)\n", *m->mh->locale);
            }
        }
        if (m->mh->dict_input_lang) {
            const char *locale_string = mobi_get_locale_string(*m->mh->dict_input_lang);
            if (locale_string) {
                LOGD("dict input lang: %s (%u)\n", locale_string, *m->mh->dict_input_lang);
            } else {
                LOGD("dict input lang: unknown (%u)\n", *m->mh->dict_input_lang);
            }
        }
        if (m->mh->dict_output_lang) {
            const char *locale_string = mobi_get_locale_string(*m->mh->dict_output_lang);
            if (locale_string) {
                LOGD("dict output lang: %s (%u)\n", locale_string, *m->mh->dict_output_lang);
            } else {
                LOGD("dict output lang: unknown (%u)\n", *m->mh->dict_output_lang);
            }
        }
        if (m->mh->min_version) { LOGD("minimal version: %u\n", *m->mh->min_version); }
        if (m->mh->image_index) { LOGD("first image index: %u\n", *m->mh->image_index); }
        if (m->mh->huff_rec_index) { LOGD("huffman record offset: %u\n", *m->mh->huff_rec_index); }
        if (m->mh->huff_rec_count) { LOGD("huffman records count: %u\n", *m->mh->huff_rec_count); }
        if (m->mh->datp_rec_index) { LOGD("DATP record offset: %u\n", *m->mh->datp_rec_index); }
        if (m->mh->datp_rec_count) { LOGD("DATP records count: %u\n", *m->mh->datp_rec_count); }
        if (m->mh->exth_flags) { LOGD("EXTH flags: %u\n", *m->mh->exth_flags); }
        if (m->mh->unknown6) { LOGD("unknown: %u\n", *m->mh->unknown6); }
        if (m->mh->drm_offset) { LOGD("drm offset: %u\n", *m->mh->drm_offset); }
        if (m->mh->drm_count) { LOGD("drm count: %u\n", *m->mh->drm_count); }
        if (m->mh->drm_size) { LOGD("drm size: %u\n", *m->mh->drm_size); }
        if (m->mh->drm_flags) { LOGD("drm flags: %u\n", *m->mh->drm_flags); }
        if (m->mh->first_text_index) { LOGD("first text index: %u\n", *m->mh->first_text_index); }
        if (m->mh->last_text_index) { LOGD("last text index: %u\n", *m->mh->last_text_index); }
        if (m->mh->fdst_index) { LOGD("FDST offset: %u\n", *m->mh->fdst_index); }
        if (m->mh->fdst_section_count) { LOGD("FDST count: %u\n", *m->mh->fdst_section_count); }
        if (m->mh->fcis_index) { LOGD("FCIS index: %u\n", *m->mh->fcis_index); }
        if (m->mh->fcis_count) { LOGD("FCIS count: %u\n", *m->mh->fcis_count); }
        if (m->mh->flis_index) { LOGD("FLIS index: %u\n", *m->mh->flis_index); }
        if (m->mh->flis_count) { LOGD("FLIS count: %u\n", *m->mh->flis_count); }
        if (m->mh->unknown10) { LOGD("unknown: %u\n", *m->mh->unknown10); }
        if (m->mh->unknown11) { LOGD("unknown: %u\n", *m->mh->unknown11); }
        if (m->mh->srcs_index) { LOGD("SRCS index: %u\n", *m->mh->srcs_index); }
        if (m->mh->srcs_count) { LOGD("SRCS count: %u\n", *m->mh->srcs_count); }
        if (m->mh->unknown12) { LOGD("unknown: %u\n", *m->mh->unknown12); }
        if (m->mh->unknown13) { LOGD("unknown: %u\n", *m->mh->unknown13); }
        if (m->mh->extra_flags) { LOGD("extra record flags: %u\n", *m->mh->extra_flags); }
        if (m->mh->ncx_index) { LOGD("NCX offset: %u\n", *m->mh->ncx_index); }
        if (m->mh->unknown14) { LOGD("unknown: %u\n", *m->mh->unknown14); }
        if (m->mh->unknown15) { LOGD("unknown: %u\n", *m->mh->unknown15); }
        if (m->mh->fragment_index) { LOGD("fragment index: %u\n", *m->mh->fragment_index); }
        if (m->mh->skeleton_index) { LOGD("skeleton index: %u\n", *m->mh->skeleton_index); }
        if (m->mh->datp_index) { LOGD("DATP index: %u\n", *m->mh->datp_index); }
        if (m->mh->unknown16) { LOGD("unknown: %u\n", *m->mh->unknown16); }
        if (m->mh->guide_index) { LOGD("guide index: %u\n", *m->mh->guide_index); }
        if (m->mh->unknown17) { LOGD("unknown: %u\n", *m->mh->unknown17); }
        if (m->mh->unknown18) { LOGD("unknown: %u\n", *m->mh->unknown18); }
        if (m->mh->unknown19) { LOGD("unknown: %u\n", *m->mh->unknown19); }
        if (m->mh->unknown20) { LOGD("unknown: %u\n", *m->mh->unknown20); }
    }
}

/**
 @brief Print meta data of each document record
 @param[in] m MOBIData structure
 */
void print_records_meta(const MOBIData *m) {
    /* Linked list of MOBIPdbRecord structures holds records data and metadata */
    const MOBIPdbRecord *currec = m->rec;
    while (currec != NULL) {
        LOGD("offset: %u\n", currec->offset);
        LOGD("size: %zu\n", currec->size);
        LOGD("attributes: %hhu\n", currec->attributes);
        LOGD("uid: %u\n", currec->uid);
        LOGD("\n");
        currec = currec->next;
    }
}

/**
 @brief Create new path. Name is derived from input file path.
        [dirname]/[basename][suffix]
 @param[out] newpath Created path
 @param[in] buf_len Created path buffer size
 @param[in] fullpath Input file path
 @param[in] suffix Path name suffix
 @return SUCCESS or ERROR
 */
MOBI_EXPORT int create_path(char *newpath, const size_t buf_len, const char *fullpath, const char *suffix) {
    char dirname[FILENAME_MAX];
    char basename[FILENAME_MAX];
    split_fullpath(fullpath, dirname, basename, FILENAME_MAX);
    int n;
    if (outdir_opt) {
        n = snprintf(newpath, buf_len, "%s%s%s", outdir, basename, suffix);
    } else {
        n = snprintf(newpath, buf_len, "%s%s%s", dirname, basename, suffix);
    }
    if (n < 0) {
        LOGD("Creating file name failed\n");
        return ERROR;
    }
    if ((size_t) n >= buf_len) {
        LOGD("File name too long\n");
        return ERROR;
    }
    return SUCCESS;
}

/**
 @brief Create directory. Path is derived from input file path.
        [dirname]/[basename][suffix]
 @param[out] newdir Created directory path
 @param[in] buf_len Created directory buffer size
 @param[in] fullpath Input file path
 @param[in] suffix Directory name suffix
 @return SUCCESS or ERROR
 */
int create_dir(char *newdir, const size_t buf_len, const char *fullpath, const char *suffix) {
    if (create_path(newdir, buf_len, fullpath, suffix) == ERROR) {
        return ERROR;
    }
    return make_directory(newdir);
}

/**
 @brief Dump each document record to a file into created folder
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to build basenames of dumped records
 @return SUCCESS or ERROR
 */
int dump_records(const MOBIData *m, const char *fullpath) {
    char newdir[FILENAME_MAX];
    if (create_dir(newdir, sizeof(newdir), fullpath, "_records") == ERROR) {
        return ERROR;
    }
    LOGD("Saving records to %s\n", newdir);
    /* Linked list of MOBIPdbRecord structures holds records data and metadata */
    const MOBIPdbRecord *currec = m->rec;
    int i = 0;
    while (currec != NULL) {
        char name[FILENAME_MAX];
        snprintf(name, sizeof(name), "record_%i_uid_%i", i++, currec->uid);
        if (write_to_dir(newdir, name, currec->data, currec->size) == ERROR) {
            return ERROR;
        }

        currec = currec->next;
    }
    return SUCCESS;
}

/**
 @brief Dump all text records, decompressed and concatenated, to a single rawml file
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to create a new name for saved file
 @return SUCCESS or ERROR
 */
int dump_rawml(const MOBIData *m, const char *fullpath) {
    char newpath[FILENAME_MAX];
    if (create_path(newpath, sizeof(newpath), fullpath, ".rawml") == ERROR) {
        return ERROR;
    }
    LOGD("Saving rawml to %s\n", newpath);
    errno = 0;
    FILE *file = fopen(newpath, "wb");
    if (file == NULL) {
        int errsv = errno;
        LOGD("Could not open file for writing: %s (%s)\n", newpath, strerror(errsv));
        return ERROR;
    }
    const MOBI_RET mobi_ret = mobi_dump_rawml(m, file);
    fclose(file);
    if (mobi_ret != MOBI_SUCCESS) {
        LOGD("Dumping rawml file failed (%s)\n", libmobi_msg(mobi_ret));
        return ERROR;
    }
    return SUCCESS;
}

/**
 @brief Dump cover record
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to create a new name for saved file
 @return SUCCESS or ERROR
 */
int dump_cover(const MOBIData *m, const char *fullpath, /*out*/char** targetPath) {
    
    MOBIPdbRecord *record = NULL;
    MOBIExthHeader *exth = mobi_get_exthrecord_by_tag(m, EXTH_COVEROFFSET);
    if (exth) {
        uint32_t offset = mobi_decode_exthvalue(exth->data, exth->size);
        size_t first_resource = mobi_get_first_resource_record(m);
        size_t uid = first_resource + offset;
        record = mobi_get_record_by_seqnumber(m, uid);
    }
    if (record == NULL || record->size < 4) {
        LOGD("Cover not found\n");
        return ERROR;
    }

    const unsigned char jpg_magic[] = "\xff\xd8\xff";
    const unsigned char gif_magic[] = "\x47\x49\x46\x38";
    const unsigned char png_magic[] = "\x89\x50\x4e\x47\x0d\x0a\x1a\x0a";
    const unsigned char bmp_magic[] = "\x42\x4d";
    
    char ext[4] = "raw";
    if (memcmp(record->data, jpg_magic, 3) == 0) {
        snprintf(ext, sizeof(ext), "%s", "jpg");
    } else if (memcmp(record->data, gif_magic, 4) == 0) {
        snprintf(ext, sizeof(ext), "%s", "gif");
    } else if (record->size >= 8 && memcmp(record->data, png_magic, 8) == 0) {
        snprintf(ext, sizeof(ext), "%s", "png");
    } else if (record->size >= 6 && memcmp(record->data, bmp_magic, 2) == 0) {
        const size_t bmp_size = (uint32_t) record->data[2] | ((uint32_t) record->data[3] << 8) |
        ((uint32_t) record->data[4] << 16) | ((uint32_t) record->data[5] << 24);
        if (record->size == bmp_size) {
            snprintf(ext, sizeof(ext), "%s", "bmp");
        }
    }
    
    char suffix[12];
    snprintf(suffix, sizeof(suffix), "_cover.%s", ext);

    char cover_path[FILENAME_MAX];
    if (create_path(cover_path, sizeof(cover_path), fullpath, suffix) == ERROR) {
        return ERROR;
    }
    
    LOGD("Saving cover to %s\n", cover_path);
    
    int ret = write_file(record->data, record->size, cover_path);
    if (ret == SUCCESS && targetPath != NULL) {
        snprintf(*targetPath, sizeof(cover_path), "%s", cover_path);
    }
    return ret;
}


/**
 @brief Dump cover record
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to create a new name for saved file
 @return SUCCESS or ERROR
 */
int dump_cover2(const MOBIData *m, const char *title, /**/const char *targetDir, /*out*/char** targetPath) {
    MOBIPdbRecord *record = NULL;
    MOBIExthHeader *exth = mobi_get_exthrecord_by_tag(m, EXTH_COVEROFFSET);
    if (exth) {
        uint32_t offset = mobi_decode_exthvalue(exth->data, exth->size);
        size_t first_resource = mobi_get_first_resource_record(m);
        size_t uid = first_resource + offset;
        record = mobi_get_record_by_seqnumber(m, uid);
    }
    if (record == NULL || record->size < 4) {
        LOGD("Cover not found\n");
        return ERROR;
    }

    const unsigned char jpg_magic[] = "\xff\xd8\xff";
    const unsigned char gif_magic[] = "\x47\x49\x46\x38";
    const unsigned char png_magic[] = "\x89\x50\x4e\x47\x0d\x0a\x1a\x0a";
    const unsigned char bmp_magic[] = "\x42\x4d";

    char ext[4] = "raw";
    if (memcmp(record->data, jpg_magic, 3) == 0) {
        snprintf(ext, sizeof(ext), "%s", "jpg");
    } else if (memcmp(record->data, gif_magic, 4) == 0) {
        snprintf(ext, sizeof(ext), "%s", "gif");
    } else if (record->size >= 8 && memcmp(record->data, png_magic, 8) == 0) {
        snprintf(ext, sizeof(ext), "%s", "png");
    } else if (record->size >= 6 && memcmp(record->data, bmp_magic, 2) == 0) {
        const size_t bmp_size = (uint32_t) record->data[2] | ((uint32_t) record->data[3] << 8) |
                                ((uint32_t) record->data[4] << 16) | ((uint32_t) record->data[5] << 24);
        if (record->size == bmp_size) {
            snprintf(ext, sizeof(ext), "%s", "bmp");
        }
    }

    char name[512];
    snprintf(name, sizeof(name), "%s_cover.%s", title, ext);

    char parent_path[FILENAME_MAX];
    snprintf(parent_path, FILENAME_MAX, "%s%s%s", targetDir, "/", "covers");    //covers 目录
    if (!dir_exists(parent_path) && make_directory(parent_path) != SUCCESS) {
        return ERROR;
    }

    char cover_path[FILENAME_MAX];
//    if (create_path(cover_path, sizeof(cover_path), targetDir, name) == ERROR) {
//        return ERROR;
//    }
    snprintf(cover_path, FILENAME_MAX, "%s%s%s", parent_path, "/" ,name);

    LOGD("Saving cover to %s\n", cover_path);

    int ret = write_file(record->data, record->size, cover_path);
    if (ret == SUCCESS && targetPath != NULL) {
        snprintf(*targetPath, sizeof(cover_path), "%s", cover_path);
    }
    return ret;
}


/**
 @brief Dump parsed markup files and resources into created folder
 @param[in] rawml MOBIRawml structure holding parsed records
 @param[in] fullpath File path will be parsed to build basenames of dumped records
 @return SUCCESS or ERROR
 */
int dump_rawml_parts(const MOBIRawml *rawml, const char *fullpath) {
    if (rawml == NULL) {
        LOGD("Rawml structure not initialized\n");
        return ERROR;
    }

    char newdir[FILENAME_MAX];
    if (create_dir(newdir, sizeof(newdir), fullpath, "_markup") == ERROR) {
        return ERROR;
    }
    LOGD("Saving markup to %s\n", newdir);

    if (create_epub_opt) {
        /* create META_INF directory */
        char opfdir[FILENAME_MAX];
        if (create_subdir(opfdir, sizeof(opfdir), newdir, "META-INF") == ERROR) {
            return ERROR;
        }

        /* create container.xml */
        if (write_to_dir(opfdir, "container.xml", (const unsigned char *) EPUB_CONTAINER, sizeof(EPUB_CONTAINER) - 1) == ERROR) {
            return ERROR;
        }

        /* create mimetype file */
        if (write_to_dir(opfdir, "mimetype", (const unsigned char *) EPUB_MIMETYPE, sizeof(EPUB_MIMETYPE) - 1) == ERROR) {
            return ERROR;
        }

        /* create OEBPS directory */
        if (create_subdir(opfdir, sizeof(opfdir), newdir, "OEBPS") == ERROR) {
            return ERROR;
        }

        /* output everything else to OEBPS dir */
        strcpy(newdir, opfdir);
    }
    char partname[FILENAME_MAX];
    if (rawml->markup != NULL) {
        /* Linked list of MOBIPart structures in rawml->markup holds main text files */
        MOBIPart *curr = rawml->markup;
        while (curr != NULL) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            snprintf(partname, sizeof(partname), "part%05zu.%s", curr->uid, file_meta.extension);
            if (write_to_dir(newdir, partname, curr->data, curr->size) == ERROR) {
                return ERROR;
            }
            LOGD("%s\n", partname);
            curr = curr->next;
        }
    }
    if (rawml->flow != NULL) {
        /* Linked list of MOBIPart structures in rawml->flow holds supplementary text files */
        MOBIPart *curr = rawml->flow;
        /* skip raw html file */
        curr = curr->next;
        while (curr != NULL) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            snprintf(partname, sizeof(partname), "flow%05zu.%s", curr->uid, file_meta.extension);
            if (write_to_dir(newdir, partname, curr->data, curr->size) == ERROR) {
                return ERROR;
            }
            LOGD("%s\n", partname);
            curr = curr->next;
        }
    }
    if (rawml->resources != NULL) {
        /* Linked list of MOBIPart structures in rawml->resources holds binary files, also opf files */
        MOBIPart *curr = rawml->resources;
        /* jpg, gif, png, bmp, font, audio, video also opf, ncx */
        while (curr != NULL) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0) {
                int n;
                if (create_epub_opt && file_meta.type == T_OPF) {
                    n = snprintf(partname, sizeof(partname), "%s%ccontent.opf", newdir, separator);
                } else {
                    n = snprintf(partname, sizeof(partname), "%s%cresource%05zu.%s", newdir, separator, curr->uid, file_meta.extension);
                }
                if (n < 0) {
                    LOGD("Creating file name failed\n");
                    return ERROR;
                }
                if ((size_t) n >= sizeof(partname)) {
                    LOGD("File name too long: %s\n", partname);
                    return ERROR;
                }
                
                if (create_epub_opt && file_meta.type == T_OPF) {
                    LOGD("content.opf\n");
                } else {
                    LOGD("resource%05zu.%s\n", curr->uid, file_meta.extension);
                }
                
                if (write_file(curr->data, curr->size, partname) == ERROR) {
                    return ERROR;
                }

            }
            curr = curr->next;
        }
    }
    return SUCCESS;
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

static char *replace(const unsigned char *original,
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



//#ifdef USE_XMLWRITER
/**
 @brief Bundle recreated source files into EPUB container
 
 This function is a simple example.
 In real world implementation one should validate and correct all input
 markup to check if it conforms to OPF and HTML specifications and
 correct all the issues.
 
 @param[in] rawml MOBIRawml structure holding parsed records
 @param[in] fullpath File path will be parsed to build basenames of dumped records
 @return SUCCESS or ERROR
 */
int create_epub(const MOBIRawml *rawml, const char *fullpath) {
    if (rawml == NULL) {
        LOGD("Rawml structure not initialized\n");
        return ERROR;
    }

    char zipfile[FILENAME_MAX];
    if (create_path(zipfile, sizeof(zipfile), fullpath, ".epub") == ERROR) {
        return ERROR;
    }
    LOGD("Saving EPUB to %s\n", zipfile);
    
    /* create zip (epub) archive */
    mz_zip_archive zip;
    memset(&zip, 0, sizeof(mz_zip_archive));
    mz_bool mz_ret = mz_zip_writer_init_file(&zip, zipfile, 0);
    if (!mz_ret) {
        LOGD("Could not initialize zip archive\n");
        return ERROR;
    }
    /* start adding files to archive */
    mz_ret = mz_zip_writer_add_mem(&zip, "mimetype", EPUB_MIMETYPE, sizeof(EPUB_MIMETYPE) - 1, MZ_NO_COMPRESSION);
    if (!mz_ret) {
        LOGD("Could not add mimetype\n");
        mz_zip_writer_end(&zip);
        return ERROR;
    }
    mz_ret = mz_zip_writer_add_mem(&zip, "META-INF/container.xml", EPUB_CONTAINER, sizeof(EPUB_CONTAINER) - 1, (mz_uint)MZ_DEFAULT_COMPRESSION);
    if (!mz_ret) {
        LOGD("Could not add container.xml\n");
        mz_zip_writer_end(&zip);
        return ERROR;
    }
    char partname[FILENAME_MAX];
    if (rawml->markup != NULL) {
        /* Linked list of MOBIPart structures in rawml->markup holds main text files */
        MOBIPart *curr = rawml->markup;
        while (curr != NULL) {
            memset(partname, '\0', FILENAME_MAX);
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            snprintf(partname, sizeof(partname), "OEBPS/part%05zu.%s", curr->uid, file_meta.extension);

            unsigned char* data = curr->data;
//            unsigned char* newData = replace(data, curr->size,  "<mbp:pagebreak/>", "                ", 1);
            unsigned char* newData = replace(data, curr->size,  "<mbp:pagebreak/>", "", 1);
            size_t newSize = strlen(newData);

//            mz_ret = mz_zip_writer_add_mem(&zip, partname, curr->data, curr->size, (mz_uint) MZ_DEFAULT_COMPRESSION);
            mz_ret = mz_zip_writer_add_mem(&zip, partname, newData, newSize, (mz_uint) MZ_DEFAULT_COMPRESSION);
            if (!mz_ret) {
                LOGD("Could not add file to archive: %s\n", partname);
                mz_zip_writer_end(&zip);
                return ERROR;
            }
            curr = curr->next;

            if (newData) {
                free(newData);
            }
        }
    }
    if (rawml->flow != NULL) {
        /* Linked list of MOBIPart structures in rawml->flow holds supplementary text files */
        MOBIPart *curr = rawml->flow;
        /* skip raw html file */
        curr = curr->next;
        while (curr != NULL) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            snprintf(partname, sizeof(partname), "OEBPS/flow%05zu.%s", curr->uid, file_meta.extension);
            mz_ret = mz_zip_writer_add_mem(&zip, partname, curr->data, curr->size, (mz_uint) MZ_DEFAULT_COMPRESSION);
            if (!mz_ret) {
                LOGD("Could not add file to archive: %s\n", partname);
                mz_zip_writer_end(&zip);
                return ERROR;
            }
            curr = curr->next;
        }
    }
    if (rawml->resources != NULL) {
        /* Linked list of MOBIPart structures in rawml->resources holds binary files, also opf files */
        MOBIPart *curr = rawml->resources;
        /* jpg, gif, png, bmp, font, audio, video, also opf, ncx */
        while (curr != NULL) {
            MOBIFileMeta file_meta = mobi_get_filemeta_by_type(curr->type);
            if (curr->size > 0) {
                if (file_meta.type == T_OPF) {
                    snprintf(partname, sizeof(partname), "OEBPS/content.opf");
                } else {
                    snprintf(partname, sizeof(partname), "OEBPS/resource%05zu.%s", curr->uid, file_meta.extension);
                }
                mz_ret = mz_zip_writer_add_mem(&zip, partname, curr->data, curr->size, (mz_uint) MZ_DEFAULT_COMPRESSION);
                if (!mz_ret) {
                    LOGD("Could not add file to archive: %s\n", partname);
                    mz_zip_writer_end(&zip);
                    return ERROR;
                }
            }
            curr = curr->next;
        }
    }
    /* Finalize epub archive */
    mz_ret = mz_zip_writer_finalize_archive(&zip);
    if (!mz_ret) {
        LOGD("Could not finalize zip archive\n");
        mz_zip_writer_end(&zip);
        return ERROR;
    }
    mz_ret = mz_zip_writer_end(&zip);
    if (!mz_ret) {
        LOGD("Could not finalize zip writer\n");
        return ERROR;
    }
    return SUCCESS;
}
//#endif

/**
 @brief Dump SRCS record
 @param[in] m MOBIData structure
 @param[in] fullpath Full file path
 @return SUCCESS or ERROR
 */
int dump_embedded_source(const MOBIData *m, const char *fullpath) {
    /* Try to get embedded source */
    unsigned char *data = NULL;
    size_t size = 0;
    MOBI_RET mobi_ret = mobi_get_embedded_source(&data, &size, m);
    if (mobi_ret != MOBI_SUCCESS) {
        LOGD("Extracting source from mobi failed (%s)\n", libmobi_msg(mobi_ret));
        return ERROR;
    }
    if (data == NULL || size == 0 ) {
        LOGD("Source archive not found\n");
        return SUCCESS;
    }

    char newdir[FILENAME_MAX];
    if (create_dir(newdir, sizeof(newdir), fullpath, "_source") == ERROR) {
        return ERROR;
    }

    const unsigned char epub_magic[] = "mimetypeapplication/epub+zip";
    const size_t em_offset = 30;
    const size_t em_size = sizeof(epub_magic) - 1;
    const char *ext;
    if (size > em_offset + em_size && memcmp(data + em_offset, epub_magic, em_size) == 0) {
        ext = "epub";
    } else {
        ext = "zip";
    }

    char srcsname[FILENAME_MAX];
    char basename[FILENAME_MAX];
    split_fullpath(fullpath, NULL, basename, FILENAME_MAX);
    int n = snprintf(srcsname, sizeof(srcsname), "%s_source.%s", basename, ext);
    if (n < 0) {
        LOGD("Creating file name failed\n");
        return ERROR;
    }
    if ((size_t) n >= sizeof(srcsname)) {
        LOGD("File name too long\n");
        return ERROR;
    }
    if (write_to_dir(newdir, srcsname, data, size) == ERROR) {
        return ERROR;
    }
    LOGD("Saving source archive to %s\n", srcsname);

    /* Try to get embedded conversion log */
    data = NULL;
    size = 0;
    mobi_ret = mobi_get_embedded_log(&data, &size, m);
    if (mobi_ret != MOBI_SUCCESS) {
        LOGD("Extracting conversion log from mobi failed (%s)\n", libmobi_msg(mobi_ret));
        return ERROR;
    }
    if (data == NULL || size == 0 ) {
        LOGD("Conversion log not found\n");
        return SUCCESS;
    }
    
    n = snprintf(srcsname, sizeof(srcsname), "%s_source.txt", basename);
    if (n < 0) {
        LOGD("Creating file name failed\n");
        return ERROR;
    }
    if ((size_t) n >= sizeof(srcsname)) {
        LOGD("File name too long\n");
        return ERROR;
    }
    if (write_to_dir(newdir, srcsname, data, size) == ERROR) {
        return ERROR;
    }
    LOGD("Saving conversion log to %s\n", srcsname);

    return SUCCESS;
}

/**
 @brief Split hybrid file in two parts
 @param[in] fullpath Full file path
 @return SUCCESS or ERROR
 */
int split_hybrid(const char *fullpath) {
    
    static int run_count = 0;
    run_count++;
    
    bool use_kf8 = run_count == 1 ? false : true;
    
    /* Initialize main MOBIData structure */
    MOBIData *m = mobi_init();
    if (m == NULL) {
        LOGD("Memory allocation failed\n");
        return ERROR;
    }

    errno = 0;
    FILE *file = fopen(fullpath, "rb");
    if (file == NULL) {
        int errsv = errno;
        LOGD("Error opening file: %s (%s)\n", fullpath, strerror(errsv));
        mobi_free(m);
        return ERROR;
    }
    /* MOBIData structure will be filled with loaded document data and metadata */
    MOBI_RET mobi_ret = mobi_load_file(m, file);
    fclose(file);
    
    if (mobi_ret != MOBI_SUCCESS) {
        LOGD("Error while loading document (%s)\n", libmobi_msg(mobi_ret));
        mobi_free(m);
        return ERROR;
    }
    
    mobi_ret = mobi_remove_hybrid_part(m, use_kf8);
    if (mobi_ret != MOBI_SUCCESS) {
        LOGD("Error removing hybrid part (%s)\n", libmobi_msg(mobi_ret));
        mobi_free(m);
        return ERROR;
    }
    
    if (save_mobi(m, fullpath, "split") != SUCCESS) {
        LOGD("Error saving file\n");
        mobi_free(m);
        return ERROR;
    }
    
    /* Free MOBIData structure */
    mobi_free(m);
    
    /* Proceed with KF8 part */
    if (use_kf8 == false) {
        split_hybrid(fullpath);
    }
    
    return SUCCESS;
}

/**
 @brief Main routine that calls optional subroutines
 @param[in] fullpath Full file path
 @return SUCCESS or ERROR
 */
int loadfilename(const char *fullpath) {
    MOBI_RET mobi_ret;
    int ret = SUCCESS;
    /* Initialize main MOBIData structure */
    MOBIData *m = mobi_init();
    if (m == NULL) {
        LOGD("Memory allocation failed\n");
        return ERROR;
    }
    /* By default loader will parse KF8 part of hybrid KF7/KF8 file */
    if (parse_kf7_opt) {
        /* Force it to parse KF7 part */
        mobi_parse_kf7(m);
    }
    errno = 0;
    FILE *file = fopen(fullpath, "rb");
    if (file == NULL) {
        int errsv = errno;
        LOGD("Error opening file: %s (%s)\n", fullpath, strerror(errsv));
        mobi_free(m);
        return ERROR;
    }
    /* MOBIData structure will be filled with loaded document data and metadata */
    mobi_ret = mobi_load_file(m, file);
    fclose(file);

    /* Try to print basic metadata, even if further loading failed */
    /* In case of some unsupported formats it may still print some useful info */
    if (print_extended_meta_opt) { print_meta(m); }
    
    if (mobi_ret != MOBI_SUCCESS) {
        LOGD("Error while loading document (%s)\n", libmobi_msg(mobi_ret));
        mobi_free(m);
        return ERROR;
    }
    
    if (create_epub_opt && mobi_is_replica(m)) {
        create_epub_opt = false;
        LOGD("\nWarning: Can't create EPUB format from Print Replica book (ignoring -e argument)\n\n");
    }
    
    if (!print_extended_meta_opt) {
        print_summary(m);
    }
    
    if (print_extended_meta_opt) {
        /* Try to print EXTH metadata */
        print_exth(m);
    }
    
#ifdef USE_ENCRYPTION
    if (setpid_opt || setserial_opt) {
        ret = set_decryption_key(m, serial, pid);
        if (ret != SUCCESS) {
            mobi_free(m);
            return ret;
        }
    }
#endif
    if (print_rec_meta_opt) {
        LOGD("\nPrinting records metadata...\n");
        print_records_meta(m);
    }
    if (dump_rec_opt) {
        LOGD("\nDumping raw records...\n");
        ret = dump_records(m, fullpath);
    }
    if (dump_rawml_opt) {
        LOGD("\nDumping rawml...\n");
        ret = dump_rawml(m, fullpath);
    } else if (dump_parts_opt || create_epub_opt) {
        LOGD("\nReconstructing source resources...\n");
        /* Initialize MOBIRawml structure */
        /* This structure will be filled with parsed records data */
        MOBIRawml *rawml = mobi_init_rawml(m);
        if (rawml == NULL) {
            LOGD("Memory allocation failed\n");
            mobi_free(m);
            return ERROR;
        }

        /* Parse rawml text and other data held in MOBIData structure into MOBIRawml structure */
        mobi_ret = mobi_parse_rawml(rawml, m);
        if (mobi_ret != MOBI_SUCCESS) {
            LOGD("Parsing rawml failed (%s)\n", libmobi_msg(mobi_ret));
            mobi_free(m);
            mobi_free_rawml(rawml);
            return ERROR;
        }
        if (create_epub_opt && !dump_parts_opt) {
#ifdef USE_XMLWRITER
            LOGD("\nCreating EPUB...\n");
            /* Create epub file */
            ret = create_epub(rawml, fullpath);
            if (ret != SUCCESS) {
                LOGD("Creating EPUB failed\n");
            }
#endif
        } else {
            LOGD("\nDumping resources...\n");
            /* Save parts to files */
            ret = dump_rawml_parts(rawml, fullpath);
            if (ret != SUCCESS) {
                LOGD("Dumping parts failed\n");
            }
        }
        /* Free MOBIRawml structure */
        mobi_free_rawml(rawml);
    }
    if (extract_source_opt) {
        ret = dump_embedded_source(m, fullpath);
    }
    if (dump_cover_opt) {
        ret = dump_cover(m, fullpath, NULL);
    }
    if (split_opt && !mobi_is_hybrid(m)) {
        LOGD("File is not a hybrid, skip splitting\n");
        split_opt = false;
    }
    /* Free MOBIData structure */
    mobi_free(m);
    return ret;
}

/**
 @brief Print usage info
 @param[in] progname Executed program name
 */
void exit_with_usage(const char *progname) {
    LOGD("usage: %s [-cd" PRINT_EPUB_ARG "himrst" PRINT_RUSAGE_ARG "vx7] [-o dir]" PRINT_ENC_USG " filename\n", progname);
    LOGD("       without arguments prints document metadata and exits\n");
    LOGD("       -c        dump cover\n");
    LOGD("       -d        dump rawml text record\n");
#ifdef USE_XMLWRITER
    LOGD("       -e        create EPUB file (with -s will dump EPUB source)\n");
#endif
    LOGD("       -h        show this usage summary and exit\n");
    LOGD("       -i        print detailed metadata\n");
    LOGD("       -m        print records metadata\n");
    LOGD("       -o dir    save output to dir folder\n");
#ifdef USE_ENCRYPTION
    LOGD("       -p pid    set pid for decryption\n");
    LOGD("       -P serial set device serial for decryption\n");
#endif
    LOGD("       -r        dump raw records\n");
    LOGD("       -s        dump recreated source files\n");
    LOGD("       -t        split hybrid file into two parts\n");
#ifdef HAVE_SYS_RESOURCE_H
    LOGD("       -u        show rusage\n");
#endif
    LOGD("       -v        show version and exit\n");
    LOGD("       -x        extract conversion source and log (if present)\n");
    LOGD("       -7        parse KF7 part of hybrid file (by default KF8 part is parsed)\n");
    exit(SUCCESS);
}

/**
 @brief Main
 
 @param[in] argc Arguments count
 @param[in] argv Arguments array
 @return SUCCESS (0) or ERROR (1)
 */
//int main0(int argc, char *argv[]) {
//    if (argc < 2) {
//        exit_with_usage(argv[0]);
//    }
//    opterr = 0;
//    int c;
//    while ((c = getopt(argc, argv, "cd" PRINT_EPUB_ARG "himo:" PRINT_ENC_ARG "rst" PRINT_RUSAGE_ARG "vx7")) != -1) {
//        switch (c) {
//            case 'c':
//                dump_cover_opt = true;
//                break;
//            case 'd':
//                dump_rawml_opt = true;
//                break;
//#ifdef USE_XMLWRITER
//            case 'e':
//                create_epub_opt = true;
//                break;
//#endif
//            case 'i':
//                print_extended_meta_opt = true;
//                break;
//            case 'm':
//                print_rec_meta_opt = true;
//                break;
//            case 'o':
//                outdir_opt = true;
//                size_t outdir_length = strlen(optarg);
//                if (outdir_length == 2 && optarg[0] == '-') {
//                    LOGD("Option -%c requires an argument.\n", c);
//                    return ERROR;
//                }
//                if (outdir_length >= FILENAME_MAX - 1) {
//                    LOGD("Output directory name too long\n");
//                    return ERROR;
//                }
//                strncpy(outdir, optarg, FILENAME_MAX - 1);
//                normalize_path(outdir);
//                if (!dir_exists(outdir)) {
//                    LOGD("Output directory is not valid\n");
//                    return ERROR;
//                }
//                if (optarg[outdir_length - 1] != separator) {
//                    // append separator
//                    if (outdir_length >= FILENAME_MAX - 2) {
//                        LOGD("Output directory name too long\n");
//                        return ERROR;
//                    }
//                    outdir[outdir_length++] = separator;
//                    outdir[outdir_length] = '\0';
//                }
//                break;
//#ifdef USE_ENCRYPTION
//            case 'p':
//                if (strlen(optarg) == 2 && optarg[0] == '-') {
//                    LOGD("Option -%c requires an argument.\n", c);
//                    return ERROR;
//                }
//                setpid_opt = true;
//                pid = optarg;
//                break;
//            case 'P':
//                if (strlen(optarg) == 2 && optarg[0] == '-') {
//                    LOGD("Option -%c requires an argument.\n", c);
//                    return ERROR;
//                }
//                setserial_opt = true;
//                serial = optarg;
//                break;
//#endif
//            case 'r':
//                dump_rec_opt = true;
//                break;
//            case 's':
//                dump_parts_opt = true;
//                break;
//            case 't':
//                split_opt = true;
//                break;
//#ifdef HAVE_SYS_RESOURCE_H
//            case 'u':
//                print_rusage_opt = true;
//                break;
//#endif
//            case 'v':
//                LOGD("mobitool build: " __DATE__ " " __TIME__ " (" COMPILER ")\n");
//                LOGD("libmobi: %s\n", mobi_version());
//                return SUCCESS;
//            case 'x':
//                extract_source_opt = true;
//                break;
//            case '7':
//                parse_kf7_opt = true;
//                break;
//            case '?':
//                if (isprint(optopt)) {
//                    fLOGD(stderr, "Unknown option `-%c'\n", optopt);
//                }
//                else {
//                    fLOGD(stderr, "Unknown option character `\\x%x'\n", optopt);
//                }
//                exit_with_usage(argv[0]);
//            case 'h':
//            default:
//                exit_with_usage(argv[0]);
//        }
//    }
//    if (argc <= optind) {
//        LOGD("Missing filename\n");
//        exit_with_usage(argv[0]);
//    }
//
//    int ret = SUCCESS;
//    char filename[FILENAME_MAX];
//    strncpy(filename, argv[optind], FILENAME_MAX - 1);
//    filename[FILENAME_MAX - 1] = '\0';
//    normalize_path(filename);
//
//    ret = loadfilename(filename);
//    if (split_opt) {
//        LOGD("\nSplitting hybrid file...\n");
//        ret = split_hybrid(filename);
//    }
//#ifdef HAVE_SYS_RESOURCE_H
//    if (print_rusage_opt) {
//        /* rusage */
//        struct rusage ru;
//        struct timeval utime;
//        struct timeval stime;
//        getrusage(RUSAGE_SELF, &ru);
//        utime = ru.ru_utime;
//        stime = ru.ru_stime;
//        LOGD("RUSAGE: ru_utime => %lld.%lld sec.; ru_stime => %lld.%lld sec.\n",
//               (long long) utime.tv_sec, (long long) utime.tv_usec,
//               (long long) stime.tv_sec, (long long) stime.tv_usec);
//    }
//#endif
//    return ret;
//}
