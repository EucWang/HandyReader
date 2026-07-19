//
// Created by MAC on 2025/4/14.
//
#include <sys/stat.h>
#include "file_searcher.h"
#include "string_ext.h"
#include "../threadlibs/scoped_interrupt_thread.h"
#include "../threadlibs/scoped_thread.h"
#include "../threadlibs/threadpool.h"
#include "../threadlibs/threadsafe_queue.h"
#include "../threadlibs/threadsafe_list.h"
std::vector<std::string> patterns;

// 全局变量（需用互斥锁保护）
threadsafe_queue<std::string> dirQueue;
threadsafe_list<std::string> results;

std::atomic<bool> stopFlag{false};

long long maxTxtFileSize = 100 * 1024L;

// Android系统目录黑名单（可根据需要扩展）
const std::vector<std::string> SYSTEM_DIRS = {
        "/proc",
        "/sys",
        "/acct",
        "/config",
        "/dev",
        "/storage/emulated/0/Android",
        "/storage/emulated/0/DCIM/.thumbnails"
};

bool shouldSkipDirectory(const std::string& path) {
    // 检查是否系统目录
    for (const auto& sysDir : SYSTEM_DIRS) {
        if (path.find(sysDir) != std::string::npos) {
            return true;
        }
    }

    // 检查目录可读性（等效于Java的canRead()）
    return access(path.c_str(), R_OK) != 0;
}


void setMaxTxtFileSize(size_t bytes) {
    maxTxtFileSize = bytes;
}

long long getMaxTxtFileSize() {
    return maxTxtFileSize;
}

// 工作线程函数
void workerThread() {
    while (!stopFlag.load()) {
        std::string currentDir;

        // 获取下一个目录（线程安全）
        dirQueue.try_pop(currentDir);
        
        if (shouldSkipDirectory(currentDir)) {
            continue; // 跳过系统/无权限目录
        }
        LOGD("workerThread::currentDir:%s,%s,%ld", currentDir.c_str(), " thread id:", std::this_thread::get_id());
        // 处理目录
        DIR* dir = opendir(currentDir.c_str());
        if (!dir) {
            LOGD("%s:opendir %s failed, pass", __func__, currentDir.c_str());
            continue;
        }
        dirent* entry;
        while ((entry = readdir(dir)) != nullptr && !stopFlag.load()) {
            std::string name = entry->d_name;
            if (name == "." || name == "..") continue;

            std::string fullPath = currentDir + "/" + name;

            if (entry->d_type == DT_DIR) {
                // 添加到队列（线程安全）
                dirQueue.push(fullPath);
            } else {
                for(const auto& pattern : patterns) {
                    std::string suffix = file_ext::get_file_suffix(name);
                    if (!suffix.empty() && string_ext::endsWithIgnoreCase(suffix, pattern)) {
                        if (pattern == "txt") {
                            struct stat file_info;
                            if (lstat(fullPath.c_str(), &file_info) == 0) {
                                if (file_info.st_size <= maxTxtFileSize) { //txt  文件大小筛选
                                    LOGD("%s:file:[%s], size:[%lld], maxTxtFileSize:[%lld], so passed", __func__, fullPath.c_str(), file_info.st_size, maxTxtFileSize);
                                    continue;
                                } else {
                                    LOGD("%s:file[%s], size[%lld] add to result", __func__, fullPath.c_str(), file_info.st_size);
                                    results.push_front(fullPath);
                                }
                            }
                        } else {
                            // 保存结果（线程安全）
                            results.push_front(fullPath);
                        }
                        LOGD("%s::workerThread::find path: %s", __func__, fullPath.c_str());
                        break;
                    }
                }
            }
        }
        closedir(dir);
    }
}

std::vector<std::string> startSearch(const std::string& root, std::vector<std::string> patts, const int threadCount) {
    // 重置状态
    stopFlag.store(false);
    results.clear();
    patterns.clear();
    patterns.assign(patts.begin(), patts.end());

    // 初始化任务队列
    dirQueue.push(root);

    threadpool pool(threadCount);
    auto woker = []{
        workerThread();
    };
    for (int i = 0; i <threadCount; ++i) {
        pool.submit(woker);
    }

    std::this_thread::sleep_for(std::chrono::seconds(1)); //sleep 1s

    // 等待线程完成（非阻塞式检查）
    while (!stopFlag.load()) {
        {
            if (dirQueue.empty()) break;
        }
        usleep(100000); // 每100ms检查一次
    }

    stopFlag.store(true); // 通知线程退出

    LOGD("%s::return results = %u", __func__, results.size());

    std::vector<std::string> result;
    while(true) {
        auto item = results.pop_front();
        if (item == nullptr) {
            break;
        }
        auto data = item.get()->c_str();
        result.push_back(data);
        LOGD("%s:add to result item is: %s", __func__, data);
    }
    return result;
}

void stopSearch(){
    stopFlag.store(true);
}