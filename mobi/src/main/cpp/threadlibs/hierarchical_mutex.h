#pragma warning (disable:4819)
#pragma once
#include <thread>
#include <mutex>
#include <exception>
#include <string>
#include <iostream>

/**
* 层次结构的mutex，
* 避免死锁问题，
* 存储一个level值，
* 如果当前线程已经持有了低level的hierarchical_mutex则不能再次lock(),
* 会直接抛出异常
*/
class HierarchicalMutex {
public:
	explicit HierarchicalMutex(unsigned long hierarchical_value);
	void lock();
	void unlock();
	bool try_lock();

private:
	std::mutex internal_mutex_;
	unsigned long const hierarchy_value_;
	unsigned long previous_hierarchy_value_;
	static thread_local unsigned long this_thread_hierarchy_value_;

	void check_for_hierarchy_violation();
	void update_hierarchy_value();

protected:
};
