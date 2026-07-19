//
// Created by MAC on 2025/4/17.
//

#ifndef SIMPLEREADER2_MOBITOOL_H
#define SIMPLEREADER2_MOBITOOL_H

#include "../log.h"
#include "../../libmobi/src/mobi.h"
#include "../../libmobi/src/miniz.h"
#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <string.h>
#include <stdbool.h>
#include <stdlib.h>
#include <ctype.h>
#include <time.h>
#include <errno.h>
/* include libmobi header */

/* miniz file is needed for EPUB creation */
//#ifdef USE_XMLWRITER
//# define MINIZ_HEADER_FILE_ONLY
# define MINIZ_NO_ZLIB_COMPATIBLE_NAMES
//#endif

#ifdef HAVE_SYS_RESOURCE_H
/* rusage */
# include <sys/resource.h>
# define PRINT_RUSAGE_ARG "u"
#else
# define PRINT_RUSAGE_ARG ""
#endif
/* encryption */
#ifdef USE_ENCRYPTION
# define PRINT_ENC_USG " [-p pid] [-P serial]"
# define PRINT_ENC_ARG "p:P:"
#else
# define PRINT_ENC_USG ""
# define PRINT_ENC_ARG ""
#endif
/* xmlwriter */
#ifdef USE_XMLWRITER
# define PRINT_EPUB_ARG "e"
#else
# define PRINT_EPUB_ARG ""
#endif

//#if HAVE_ATTRIBUTE_NORETURN
//void exit_with_usage(const char *progname) __attribute__((noreturn));
//#else
//void exit_with_usage(const char *progname);
//#endif

#define EPUB_CONTAINER "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\
<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n\
  <rootfiles>\n\
    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n\
  </rootfiles>\n\
</container>"
#define EPUB_MIMETYPE "application/epub+zip"



/**
 @brief Print all loaded headers meta information
 @param[in] m MOBIData structure
 */
void print_meta(const MOBIData *m);

/**
 @brief Print meta data of each document record
 @param[in] m MOBIData structure
 */
void print_records_meta(const MOBIData *m);
/**
 @brief Create new path. Name is derived from input file path.
        [dirname]/[basename][suffix]
 @param[out] newpath Created path
 @param[in] buf_len Created path buffer size
 @param[in] fullpath Input file path
 @param[in] suffix Path name suffix
 @return SUCCESS or ERROR
 */
MOBI_EXPORT int create_path(char *newpath, const size_t buf_len, const char *fullpath, const char *suffix);
/**
 @brief Create directory. Path is derived from input file path.
        [dirname]/[basename][suffix]
 @param[out] newdir Created directory path
 @param[in] buf_len Created directory buffer size
 @param[in] fullpath Input file path
 @param[in] suffix Directory name suffix
 @return SUCCESS or ERROR
 */
int create_dir(char *newdir, const size_t buf_len, const char *fullpath, const char *suffix);
/**
 @brief Dump each document record to a file into created folder
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to build basenames of dumped records
 @return SUCCESS or ERROR
 */
int dump_records(const MOBIData *m, const char *fullpath);
/**
 @brief Dump all text records, decompressed and concatenated, to a single rawml file
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to create a new name for saved file
 @return SUCCESS or ERROR
 */
int dump_rawml(const MOBIData *m, const char *fullpath);
/**
 @brief Dump cover record
 @param[in] m MOBIData structure
 @param[in] fullpath File path will be parsed to create a new name for saved file
 @param[out] targetPath output Fil path of target cover record
 @return SUCCESS or ERROR
 */
int dump_cover(/*in*/const MOBIData *m, /*in*/const char *fullpath, /*out*/char** targetPath);

int dump_cover2(/*in*/const MOBIData *m, /*in*/const char *fullpath, /**/const char *targetDir, /*out*/char** targetPath);


/**
 @brief Dump parsed markup files and resources into created folder
 @param[in] rawml MOBIRawml structure holding parsed records
 @param[in] fullpath File path will be parsed to build basenames of dumped records
 @return SUCCESS or ERROR
 */
int dump_rawml_parts(const MOBIRawml *rawml, const char *fullpath);

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
int create_epub(const MOBIRawml *rawml, const char *fullpath);

/**
 @brief Dump SRCS record
 @param[in] m MOBIData structure
 @param[in] fullpath Full file path
 @return SUCCESS or ERROR
 */
int dump_embedded_source(const MOBIData *m, const char *fullpath);
/**
 @brief Split hybrid file in two parts
 @param[in] fullpath Full file path
 @return SUCCESS or ERROR
 */
int split_hybrid(const char *fullpath);
/**
 @brief Main routine that calls optional subroutines
 @param[in] fullpath Full file path
 @return SUCCESS or ERROR
 */
//int loadfilename(const char *fullpath);

/**
 @brief Print usage info
 @param[in] progname Executed program name
 */
//void exit_with_usage(const char *progname);

//#endif
#ifdef __cplusplus
};
#endif

#endif //SIMPLEREADER2_MOBITOOL_H
