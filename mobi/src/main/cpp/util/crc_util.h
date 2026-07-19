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

// CRC + SHA256 计算结果结构体
// ★ 2026-07-07 方案 A+:同一次文件 IO 同时算 CRC32 + SHA-256,
//   避免后续上层(Java ContentHashCalculator)再次全文件读算 hash。
//   见 docs/plans/2026-07-07-扫描导入同书去重.md §四-A+
struct FileCrcResult {
    fs::path filepath;
    uint32_t crc;
    std::string sha256;  // 64 字符 hex(SHA-256 全文件);失败时为空字符串
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
