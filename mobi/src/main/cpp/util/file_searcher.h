//
// Created by MAC on 2025/4/14.
//

#ifndef SIMPLEREADER2_FILE_SEARCHER_H
#define SIMPLEREADER2_FILE_SEARCHER_H

#include "log.h"
#include "file_ext.h"
#include <dirent.h>
#include <mutex>
#include <queue>
#include <thread>
#include <vector>
#include <atomic>
#include <unistd.h>

void setMaxTxtFileSize(size_t bytes);

long long getMaxTxtFileSize();

std::vector<std::string> startSearch(const std::string& root, std::vector<std::string> pattern, const int threadCount);

void stopSearch();

#endif //SIMPLEREADER2_FILE_SEARCHER_H
