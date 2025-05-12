//
// Created by MAC on 2025/4/17.
//

#ifndef SIMPLEREADER2_MOBIDRM_H
#define SIMPLEREADER2_MOBIDRM_H

#include "../../libmobi/src/mobi.h"
#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <string.h>
#include <stdbool.h>
#include <stdlib.h>
#include <ctype.h>
#include <errno.h>

#define VOUCHERS_COUNT_MAX 20


/**
 @brief Print usage info
 @param[in] progname Executed program name
 */
//static void print_usage(const char *progname);
/**
 @brief Applies DRM

 @param[in,out] m MOBIData structure
 @param[in] use_kf8 In case of hybrid file process KF8 part if true, the other part otherwise
 @return SUCCESS or ERROR
 */
static int do_encrypt(MOBIData *m, bool use_kf8);
/**
@brief Removes DRM

@param[in,out] m MOBIData structure
@return SUCCESS or ERROR
*/
static int do_decrypt(MOBIData *m);
/**
@brief Main routine that calls optional subroutines

@param[in] fullpath Full file path
@return SUCCESS or ERROR
*/
//static int loadfilename(const char *fullpath);
/**
 @brief Parse ISO8601 date into tm structure

 @param[in,out] tm Structure to be filled
 @param[in] date_str Input date string
 @return SUCCESS or ERROR
 */
static int parse_date(struct tm *tm, const char *date_str);


#ifdef __cplusplus
};
#endif


#endif //SIMPLEREADER2_MOBIDRM_H
