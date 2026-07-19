#pragma once

#include <atomic>
#include <memory>
#include <iostream>
#include <thread>

/**
* 最大500个元素
**/
unsigned const max_hazard_pointers = 500;

struct hazard_pointer {
	std::atomic<std::thread::id> m_Id;
	std::atomic<void*> pointer;
};

/**
* 存储hazrd_pointer线程id和指针对的数组
*/
hazard_pointer hazard_pointers[max_hazard_pointers];

/**
* 风险指针存储类
* 此类和线程绑定,thread_local类型
*/
class hp_owner {
private:
	hazard_pointer* m_Hp;
public:
	/**
	* 禁用拷贝构造函数
	*/
	hp_owner(hp_owner const&) = delete;

	/**
	* 禁用赋值运算符
	*/
	hp_owner operator=(hp_owner const& other) = delete;

	/**
	* 构造函数
	*/
	hp_owner(): m_Hp(nullptr) {
		for (unsigned i = 0; i < max_hazard_pointers; ++i) {
			std::thread::id old_id;
			//遍历数组,找到一个空位置
			if (hazard_pointers[i].m_Id.compare_exchange_strong(
				old_id,
				std::this_thread::get_id())) {
				m_Hp = &hazard_pointers[i];
				break;
			}
		}
		if (!m_Hp) { //没有获取到值
			throw std::runtime_error("No hazard pointers avaiable!");
		}
	}

	/**
	* 获取风险指针
	*/
	std::atomic<void *>& get_pointer() {
		return m_Hp->pointer;
	}

	/**
	* 析构函数
	*/
	~hp_owner() {
		m_Hp->pointer.store(nullptr);        //存储null
		m_Hp->m_Id.store(std::thread::id()); //存储一个空的ID
	}

	/**
	* 返回和当前线程对应的风险指针
	**/
	static std::atomic<void*>& get_hazard_pointer_for_current_thread() {
		thread_local static hp_owner hazard;
		return hazard.get_pointer();
	}

	/**
	* 检测一个指针是否有风险指针引用到了
	*/
	static bool outstanding_hazard_pointers_for(void* p) {
		for (unsigned i = 0; i < max_hazard_pointers; i++) {
			if (hazard_pointers[i].pointer == p) {
				return true;
			}
		}
		return false;
	}
};