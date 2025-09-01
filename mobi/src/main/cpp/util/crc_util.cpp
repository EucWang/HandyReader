//
// Created by MAC on 2025/4/15.
//

#include "crc_util.h"

// CRC-32多项式(以太网标准)
constexpr uint32_t POLYNOMIAL = 0x04C11DB7;

// CRC表(编译时生成)
constexpr auto generate_crc_table() {
    std::array<uint32_t, 256> table{};
    for (uint32_t i = 0; i < table.size(); ++i) {
        uint32_t crc = i << (sizeof(uint32_t) * 8 - 8);
        for (int j = 0; j < 8; ++j) {
            crc = (crc & (1u << 31)) ? (crc << 1) ^ POLYNOMIAL : (crc << 1);
        }
        table[i] = crc;
    }
    return table;
}

static constexpr auto crc_table = generate_crc_table();


// CRC工作线程函数(并行处理)
void CrcResults::process_files_crc(const std::vector<std::string> &files, size_t start, size_t end) {
    std::vector<fs::path> paths;
    for(auto file : files) {
        paths.push_back(fs::path(file));
    }
    process_files_crc(paths, start, end);
}

void CrcResults::process_files_crc(const std::vector<fs::path> &files, size_t start, size_t end) {
    constexpr size_t buffer_size = 64 * 1024; //64KB缓冲区

    for (size_t i = start; i < end && i < files.size(); ++i) {
        const auto &filepath = files[i];

        try {
            std::ifstream file(filepath, std::ios::binary | std::ios::ate);
            if (!file) {
                throw std::runtime_error("无法打开文件");
            }

            auto file_size = file.tellg();
            file.seekg(0, std::ios::beg);

            uint32_t crc = 0xFFFFFFFFu;
            std::vector<char> buffer(buffer_size);

            while (file.read(buffer.data(), buffer.size())) {
                for (size_t j = 0; j < file.gcount(); ++j) {
                    crc = (crc >> 8) ^ crc_table[(crc & 0xFF) ^ static_cast<uint8_t>(buffer[j])];
                }
            }

            //处理剩余字节(如果有)
            for (size_t j = 0; j < file.gcount(); ++j) {
                crc = (crc >> 8) ^ crc_table[(crc & 0xFF) ^ static_cast<uint8_t>(buffer[j])];
            }

            add({filepath, crc ^ 0xFFFFFFFFu});

        } catch (const std::exception &e) {
            add({filepath, static_cast<uint32_t>(-1)}); //标记错误状态(-1)
            continue;
        }
    }
}

//主函数:批量处理目录下所有文件的CRCs(并行)
//void batch_process_crcs(const fs::path &directory_path, unsigned int thread_count = std::thread::hardware_concurrency()) {
//
//    if (!fs::exists(directory_path)) {
//        throw std::runtime_error("目录不存在");
//    }
//
//    //收集所有常规文件路径(非目录)
//    std::vector<fs::path> files;
//    for (const auto &entry: fs::recursive_directory_iterator(directory_path)) {
//        if (entry.is_regular_file()) {
//            files.push_back(entry.path());
//        }
//    }
//
//    if (files.empty()) {
//        throw std::runtime_error("目录中没有可处理的文件");
//    }
//
//    //根据CPU核心数确定线程数(最小为1)
//    thread_count = std::max(thread_count, 1u);
//
//    //分配任务给各线程(均分)
//    const size_t chunk_size = files.size() / thread_count;
//
//    CrcResults results;
//    std::vector<std::thread> threads;
//
//    auto start_time = std::chrono::high_resolution_clock::now();
//
//    //启动工作线程池(并行处理)
//    for (unsigned int i = 0; i < thread_count; ++i) {
//        size_t start = i * chunk_size;
//        size_t end = (i == thread_count - 1) ? files.size() : (i + 1) * chunk_size;
//
//        threads.emplace_back(process_files, std::cref(files), std::ref(results), start, end);
//    }
//
//    //等待所有线程完成工作(join)
//    for (auto &thread: threads) {
//        thread.join();
//    }
//
//    auto end_time = std::chrono::high_resolution_clock::now();
//
//    //输出结果统计信息(性能分析)
//    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time).count();
//
//    std::cout << "已处理" << results.get().size() << "个文件的CRCs\n";
//    std::cout << "总耗时:" << duration << "毫秒\n";
//
//    //输出每个文件的CRCs(可选)
//    for (const auto &result: results.get()) {
//        if (result.crc == static_cast<uint32_t>(-1)) {
//            std::cout << "[错误]" << result.filepath.string() << "\n";
//        } else {
//            std::cout << result.filepath.string() << ": " << std::hex << result.crc << "\n";
//        }
//    }
//}