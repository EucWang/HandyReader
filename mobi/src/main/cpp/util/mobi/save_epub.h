//
// Created by MAC on 2025/4/21.
//

#ifndef SIMPLEREADER2_SAVE_EPUB_H
#define SIMPLEREADER2_SAVE_EPUB_H

#include "common.h"
#include "../string_ext.h"

#ifdef __cplusplus

extern "C" {
int epub_rawml_parts(const MOBIRawml *rawml, const char *epub_fn);
MOBIRawml *loadMobiRawml(MOBIData *m,
                         const char *mobiFn,
                         const char *pid = NULL,
                         bool parse_kf7_opt = false);

int convertMobiToEpub(const char *mobiFn,
                      const char *epubFn,
                      const char *pid = NULL,
                      bool parse_kf7_opt = false);
};

#else

extern int epubrawml_parts(const MOBIRawml *rawml, const char *epub_fn);
extern MOBIRawml *loadMobiRawml(MOBIData *m,
                         const char *mobiFn,
                         const char *pid = NULL,
                         bool parse_kf7_opt = false);
extern int convertMobiToEpub(const char *mobiFn,
                      const char *epubFn,
                      const char *pid = NULL,
                      bool parse_kf7_opt = false);

#endif

#endif //SIMPLEREADER2_SAVE_EPUB_H
