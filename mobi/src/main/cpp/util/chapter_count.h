//
// Created by MAC on 2025/6/25.
//

#ifndef U_READER2_CHAPTER_COUNT_H
#define U_READER2_CHAPTER_COUNT_H

#include <stdint.h>

typedef struct _ChapterCount {
    int chapterOrder;
    size_t words;
    size_t pics;
} ChapterCount;

#endif //U_READER2_CHAPTER_COUNT_H
