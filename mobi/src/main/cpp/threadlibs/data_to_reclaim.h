#pragma once

#include <atomic>
#include <algorithm>
#include <functional>
#include "hp_owner.h"
/**
* 对void* 类型的指针进行删除
*/
template<typename T>
void do_delete(void* p) {
	delete static_cast<T*>(p);
}
/**
* 维护一个待删除对象的队列,
* reclaim_later()函数将待删除对象放入队列中,
* delete_nodes_with_no_hazards()函数则判断队列中的对象如果不是危险指针(有引用指向的指针),
* 则删除
**/
template<typename T>
class data_reclaim {
private:
	/* 链表结构的形式存储的待删除的指针项,析构时自动删除对象**/
	struct data_to_reclaim {
		void* data;							 //待删除的数据指针
		std::function<void(void*)> deleter;  //删除器(删除指针的函数)
		data_to_reclaim* next;				 //下一个结构体
		/**
		* 构造函数
		*/
		// template<typename T>
		data_to_reclaim(T* p) : data(p), deleter(do_delete<T>), next(nullptr) {}
		/*
		**析构函数
		*/
		~data_to_reclaim() { this->deleter(data); }
	};
	
	//----------------------------------------------------------------------
	
	/**头节点*/
	std::atomic<data_to_reclaim*> nodes_to_reclaim;
	
	/**
	* 将一个新节点放入到链表结构中
	**/
	void add_to_reclaim_list(data_to_reclaim* node) {
		node->next = nodes_to_reclaim.load();
		while(!nodes_to_reclaim.compare_exchange_weak(node->next, node));
	}

	static data_reclaim<T>* _instance;

	data_reclaim():nodes_to_reclaim(nullptr) {}

	/**
	* 判断队列中的对象如果不是危险指针(有引用指向的指针), 则删除
	*/
	void _delete_nodes_with_no_hazards() {
		//获取头节点
		data_to_reclaim* current = nodes_to_reclaim.exchange(NULL, std::memory_order_seq_cst);
		while (current) {
			data_to_reclaim* next = current->next;
			//指针没有其他引用,则删除
			if (!hp_owner::outstanding_hazard_pointers_for(current->data)) {
				delete current;
			}
			else { //有其他引用,则保留
				add_to_reclaim_list(current); 
			}
			current = next;
		}
	}
public:
	~data_reclaim() {}
	/**
	* 获取单例对象
	*/
	static data_reclaim<T>* instance() {
		if (_instance == nullptr) {
			_instance = new data_reclaim();
		}
		return _instance;
	}
	void reclaim_later(T* data) {
		add_to_reclaim_list(new data_to_reclaim(data));
	}
	void delete_nodes_with_no_hazards(){
		_delete_nodes_with_no_hazards();
	}
protected:
};
template<typename T>
data_reclaim<T>* data_reclaim<T>::_instance = nullptr;