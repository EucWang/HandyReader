//
// Created by MAC on 2025/4/17.
//

#ifndef SIMPLEREADER2_MOBIMETA_H
#define SIMPLEREADER2_MOBIMETA_H

#include "../../libmobi/src/mobi.h"
#include "common.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>

#include <stdlib.h>
#include <ctype.h>
#include <errno.h>
#include <string.h>


/* encryption */
#ifdef USE_ENCRYPTION
# define PRINT_ENC_USG " [-p pid] [-P serial]"
# define PRINT_ENC_ARG "p:P:"
#else
# define PRINT_ENC_USG ""
# define PRINT_ENC_ARG ""
#endif


#define META_SIZE ARRAYSIZE(meta_functions)
#define ACTIONS_SIZE ARRAYSIZE(actions)

//#if HAVE_ATTRIBUTE_NORETURN
//static void exit_with_usage(const char *progname) __attribute__((noreturn));
//#else
//
//static void exit_with_usage(const char *progname);
//
//#endif

/**
 @brief Meta handling functions
 */
typedef MOBI_RET (*MetaFunAdd)(MOBIData *m, const char *string);

typedef MOBI_RET (*MetaFunDel)(MOBIData *m);

/**
 @brief Meta functions structure
 */
typedef struct {
    const char *name;
    MetaFunAdd function_add;
    MetaFunAdd function_set;
    MetaFunDel function_del;
} CB;

const CB meta_functions[] = {
        {"author",      mobi_meta_add_author,      mobi_meta_set_author,      mobi_meta_delete_author},
        {"title",       mobi_meta_add_title,       mobi_meta_set_title,       mobi_meta_delete_title},
        {"publisher",   mobi_meta_add_publisher,   mobi_meta_set_publisher,   mobi_meta_delete_publisher},
        {"imprint",     mobi_meta_add_imprint,     mobi_meta_set_imprint,     mobi_meta_delete_imprint},
        {"description", mobi_meta_add_description, mobi_meta_set_description, mobi_meta_delete_description},
        {"isbn",        mobi_meta_add_isbn,        mobi_meta_set_isbn,        mobi_meta_delete_isbn},
        {"subject",     mobi_meta_add_subject,     mobi_meta_set_subject,     mobi_meta_delete_subject},
        {"publishdate", mobi_meta_add_publishdate, mobi_meta_set_publishdate, mobi_meta_delete_publishdate},
        {"review",      mobi_meta_add_review,      mobi_meta_set_review,      mobi_meta_delete_review},
        {"contributor", mobi_meta_add_contributor, mobi_meta_set_contributor, mobi_meta_delete_contributor},
        {"copyright",   mobi_meta_add_copyright,   mobi_meta_set_copyright,   mobi_meta_delete_copyright},
        {"asin",        mobi_meta_add_asin,        mobi_meta_set_asin,        mobi_meta_delete_asin},
        {"language",    mobi_meta_add_language,    mobi_meta_set_language,    mobi_meta_delete_language},
};


/**
@brief Print usage info
@param[in] progname Executed program name
*/
//static void exit_with_usage(const char *progname);
/**
@brief Check whether string is integer
@param[in] string String
@return True if string represents integer
*/
static bool isinteger(const char *string);

/**
 @brief Parse suboption's list key=value[,key=value,...]
 and get first key-value pair.
 @param[in,out] subopts List of suboptions
 @param[in,out] token Will be filled with first found key name or NULL if missing
 @param[in,out] value Will be filled with first found key value or NULL if missing
 @return True if there are more pairs to parse
 */
static bool parsesubopt(char **subopts, char **token, char **value);
/**
 @brief Get matching token from meta functions array
 @param[in] token Meta token name
 @return Index in array,-1 if not found
 */
static int get_meta(const char *token);


#ifdef __cplusplus
};
#endif


#endif //SIMPLEREADER2_MOBIMETA_H
