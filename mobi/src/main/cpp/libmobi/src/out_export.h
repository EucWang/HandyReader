//
// Created by MAC on 2025/4/18.
//

#ifndef SIMPLEREADER2_OUT_EXPORT_H
#define SIMPLEREADER2_OUT_EXPORT_H


/** @brief Visibility attributes for symbol export */
#if defined (__CYGWIN__) || defined (__MINGW32__)
#define MOBI_EXPORT __attribute__((visibility("default"))) __declspec(dllexport) extern
#elif defined (_WIN32)
#define MOBI_EXPORT __declspec(dllexport)
#else
#define MOBI_EXPORT __attribute__((__visibility__("default")))
#endif


#endif //SIMPLEREADER2_OUT_EXPORT_H
