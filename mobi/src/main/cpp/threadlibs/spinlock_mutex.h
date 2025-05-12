#pragma warning (disable:4819)
#pragma once

#include <iostream>
#include <atomic>
#include <string>

/**
 * 用std::atomic_flag模拟实现的自旋锁
*/
class SpinlockMutex{
public:
    void Lock() {
        while(flag_.test_and_set(std::memory_order_acquire));
    }
    void Unlock() {
        flag_.clear(std::memory_order_release);
    }
private:
    std::atomic_flag flag_ = ATOMIC_FLAG_INIT;
protected:
};
