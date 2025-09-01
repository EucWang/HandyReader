//
// Created by MAC on 2025/4/15.
//

#ifndef SIMPLEREADER2_CRC_UTIL_H
#define SIMPLEREADER2_CRC_UTIL_H

#include <iostream>
#include <fstream>
#include <vector>
#include <filesystem>
#include <chrono>
#include <thread>
#include <mutex>

namespace fs = std::filesystem;

// CRC计算结果结构体
struct FileCrcResult {
    fs::path filepath;
    uint32_t crc;
};

// CRC计算结果容器(线程安全)
class CrcResults {
private:
    std::vector<FileCrcResult> results_;
    mutable std::mutex mutex_;
public:
    void add(const FileCrcResult &result) {
        std::lock_guard<std::mutex> lock(mutex_);
        results_.push_back(result);
    }

    const auto &get() const { return results_; }

    void process_files_crc(const std::vector<fs::path> &files, size_t start, size_t end);
    void process_files_crc(const std::vector<std::string> &files, size_t start, size_t end);
};


#endif //SIMPLEREADER2_CRC_UTIL_H
