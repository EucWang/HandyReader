#pragma warning (disable:4819)
#pragma once
#ifndef H_THREADSAFE_QUEUE_
#define H_THREADSAFE_QUEUE_

#include <thread>
#include <mutex>
#include <queue>
#include <condition_variable>
#include <memory> 
#include <iostream>   

/**
* 线程安全的队列实现,基于std::queue的实现
*/
template<class T>
class threadsafe_queue_v1 {
public:
	/**
	* 默认构造函数
	*/
	explicit threadsafe_queue_v1(){}

	/**
	* 拷贝构造函数
	*/
	threadsafe_queue_v1(const threadsafe_queue_v1& other) {
		std::lock_guard<std::mutex> lock(other.m_Mutex);
		m_Queue = other.m_Queue;
	}

	/**
	 禁用赋值运算符
	*/
	threadsafe_queue_v1& operator=(const threadsafe_queue_v1&) = delete;

	/**
	* 将元素压入队列尾部
	*/
	void push(T new_value) {
		std::lock_guard<std::mutex> lock(m_Mutex);
		std::shared_ptr<T> data(std::make_shared<T>(std::move(new_value)));
		this->m_Queue.push(data);
		this->m_Cond.notify_one();
	}
 
	/**
	* 从对首部弹出元素, 以shared_ptr方式获取
	* 立即返回,如果失败返回false, top_value为null,
	* 成功返回true
	*/
	std::shared_ptr<T> try_pop() {
		std::lock_guard<std::mutex> lock(m_Mutex);
		if (this->m_Queue.empty()) {
			return nullptr;
		}
		std::shared_ptr<T> result = this->m_Queue.front();
		this->m_Queue.pop();
		return result;
	}

	/**
	* 从对首部弹出元素, 以shared_ptr方式获取, 阻塞方式获取,直到获取到元素
	*/
	std::shared_ptr<T> pop_or_wait() {
		std::unique_lock<std::mutex> lock(m_Mutex);
		m_Cond.wait(lock, [this] { return !m_Queue.empty();});
		std::shared_ptr<T> result = m_Queue.front();
		this->m_Queue.pop();
		return result;
	}

	/**
	* 获取队列是否为空
	*/
	bool empty() const {
		std::lock_guard<std::mutex> lock(m_Mutex);
		return m_Queue.empty();
	}

private:
    mutable std::mutex m_Mutex;
	std::queue<std::shared_ptr<T>> m_Queue;
	std::condition_variable m_Cond;
protected:
};

/**
 * 基于自定义的node链表结构实现的队列
 * 提供细粒度锁机制
*/
template<class T>
class threadsafe_queue_v2 {
public:
	/**
	 * 构造函数
	*/
	threadsafe_queue_v2() :
		head(new Node),
		tail(head.get()) {
	}
	/**
	 * 拷贝构造函数
	*/
	threadsafe_queue_v2(const threadsafe_queue_v2& other) = delete;
	/**
	 * 禁用赋值运算符
	*/
	threadsafe_queue_v2& operator=(const threadsafe_queue_v2& other) = delete;
	/**
	 * 非阻塞方式获取栈顶元素
	*/
	std::shared_ptr<T> try_pop()
	{
		std::unique_ptr<threadsafe_queue_v2<T>::Node> old_head = try_pop_head();
		return old_head ? (old_head->data) : (std::shared_ptr<T>());
	}
	/**
	 * 非阻塞方式获取栈顶元素
	*/
	bool try_pop(T& value) {
		std::unique_ptr<Node> const old_head = try_pop_head(value);
		return old_head != nullptr;
	}
	/**
	 * 阻塞方式获取栈顶元素
	*/
	std::shared_ptr<T> wait_and_pop()
	{
		std::unique_ptr<Node> old_head = wait_pop_head();
		return old_head->data;
	}

	/**
	 * 将新元素放入队列尾部
	*/
	void push(T new_value) {
		//包装数据到智能指针中
		std::shared_ptr<T> new_data(std::make_shared<T>(std::move(new_value)));
		///构造一个空的节点,作为尾节点
		std::unique_ptr<Node> p(new Node);

		//锁定尾节点互斥元
		std::unique_lock<std::mutex> lock(tail_mutex);
		this->tail->data = new_data; //为上一个尾节点赋予data数据 
		Node* const new_tail = p.get();    //获取新的尾节点的裸指针
		this->tail->next = std::move(p);   //为上一个尾节点赋予next,指向新的尾节点
		this->tail = new_tail;             //设置新的尾节点

		lock.unlock();                      // 释放锁
		data_cond.notify_one();             //唤醒条件空等待
	}
	/**
	 * 队列是否为空
	*/
	bool empty()
	{
		std::lock_guard<std::mutex> head_lock(head_mutex);
		return head.get() == get_tail();
	}

    /***
     * 清空队列全部元素
     * 阻塞方式清空
     */
    void clear() {
        while (!empty()){
           wait_and_pop();
        }
    }
private:
	struct Node {             //链表节点结构体
		std::shared_ptr<T> data;
		std::unique_ptr<Node> next;
	};
	std::mutex head_mutex;  //头部锁
	std::mutex tail_mutex;  //尾部锁
	std::unique_ptr<Node> head;  //头指针
	Node* tail;                  //尾部指针
	std::condition_variable data_cond;   //队列空的条件变量


	/**
	 * 加锁的获取尾部节点
	*/
	Node* get_tail() {
		std::lock_guard<std::mutex> tail_lock(tail_mutex);
		return tail;
	}
	/**
	 * 无锁的弹出队首元素
	*/
	std::unique_ptr<Node> pop_head() {
		std::unique_ptr<Node> new_head = std::move(head->next);
		std::unique_ptr<Node> old_head = std::move(head);
		head = std::move(new_head);
		return old_head;
	}
	/**
	 * 加锁并启动条件等待,然后返回锁
	*/
	std::unique_lock<std::mutex> wait_for_data() {
		std::unique_lock<std::mutex> head_lock(head_mutex);
		this->data_cond.wait(head_lock, [&] {
			return head.get() != get_tail();
		});
		return std::move(head_lock);
	}

	/**
	 * 加锁并启动条件等待,然后获取队首元素
	*/
	std::unique_ptr<Node> wait_pop_head() {
		std::unique_lock<std::mutex> head_lock(wait_for_data());
		return pop_head();
	}
	/**
	 * 加锁并启动条件等待,然后获取队首元素
	*/
	std::unique_ptr<Node> wait_pop_head(T& value) {
		std::unique_lock<std::mutex> head_lock(wait_for_data());
		value = std::move(*(head->data));
		return pop_head();
	}

	/**
	 * 非阻塞等待的获取头部元素
	 * 如果队列为空则返回空
	*/
	std::unique_ptr<Node> try_pop_head() {
		std::lock_guard<std::mutex> head_lock(head_mutex);
		if (head.get() == get_tail()) {
			return std::unique_ptr<threadsafe_queue_v2<T>::Node>();
		}
		return std::move(pop_head());
	}

	/**
	 * 非阻塞等待的获取头部元素
	 * 如果队列为空则返回空
	*/
	std::unique_ptr<Node> try_pop_head(T& value) {
		std::lock_guard<std::mutex> head_lock(head_mutex);
		if (head.get() == get_tail()) {
			return std::unique_ptr<threadsafe_queue_v2<T>::Node>();
		}
		value = std::move(*(head->data));
		return pop_head();
	}
	
protected:
};


template<class T>
using threadsafe_queue = threadsafe_queue_v2<T>;


#endif