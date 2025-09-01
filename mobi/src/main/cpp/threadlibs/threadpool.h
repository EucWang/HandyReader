#pragma once

#include <atomic>
#include <memory>
#include <functional>
#include <algorithm>
#include <thread>
#include <future>
#include <queue>
#include <iostream>
#include <deque>
#include <vector>

#include "threadsafe_queue.h"
#include "scoped_thread.h"
#include "spinlock_mutex.h"

/**
* 最简单版本的线程池实现
*/
class threadpool_v1 {
private:
	std::atomic_bool m_Done;
	threadsafe_queue<std::function<void()>> m_WorkQueue;
	std::vector<std::thread> m_Threads;
	//join_threads joiner;

	void worker_thread() {
		while (!m_Done) {
			std::function<void()> task;
			if (m_WorkQueue.try_pop(task)) {
				task();
			}
			else {
				std::this_thread::yield();
				// std::cout << __FUNCTION__ << " yield." << std::endl;
			}
		}
		// std::cout << __FUNCTION__ << " done job." << std::endl;
	}
public:
	/**
	* 构造函数
	*/
	explicit threadpool_v1()
		: m_Done(false) {
		unsigned const thread_count = std::thread::hardware_concurrency();
		std::cout << __FUNCTION__ << " thread_count is " << thread_count << std::endl;
		try {
			for (unsigned i = 0; i < thread_count; ++i) { 
				m_Threads.push_back(std::thread(&threadpool_v1::worker_thread, this));
			}
		}
		catch (...) {
			m_Done = true;
			throw;
		}
	}

	/**
	* 析构函数
	*/
	~threadpool_v1() {
		m_Done = true;
		for (auto it = m_Threads.begin(); it != m_Threads.end(); ++it) {
			if ((*it).joinable()) {
				(*it).join();
			}
		}
		std::cout << __FUNCTION__ << " done." << std::endl;
	}

	template<typename FunctionType> 
	void submit(FunctionType f) {
		m_WorkQueue.push(std::function<void()>(f));
	}
protected:
};


/***
 * 任务包装类
*/
class function_wrapper {
private:
	/**任务基类**/
	struct impl_base {
		virtual void call() = 0;
		virtual ~impl_base() {}
	};

	std::unique_ptr<impl_base> impl;

	/**任务具体实现类***/
	template<typename F>
	struct impl_type: impl_base {
		F f;
		impl_type(F&& f_): f(std::move(f_)) {}
		void call() {f(); }
	};
public:
	/**任务包装类的构造函数***/
	template<typename F>
	function_wrapper(F&& f_):impl(new impl_type<F>(std::move(f_))) {}
	/**默认构造函数***/
	function_wrapper() {}

	/**小括号操作符****/
	void operator()(){ impl->call();}

	/**移动构造函数***/
	function_wrapper(function_wrapper&& other) :
		impl(std::move(other.impl)) {}
	/**移动赋值语句***/
	function_wrapper& operator=(function_wrapper&& other) {
		impl = std::move(other.impl);
		return *this;
	}

	//无拷贝构造函数和拷贝赋值运算符重载
	function_wrapper(function_wrapper& other) = delete;
	function_wrapper& operator=(const function_wrapper& other) = delete;
protected:
};

/**
 * 第二个版本的线程池实现
 * 用functio_wrapper来包装任务,
 * submit提交任务的时候,返回future,从而可以获取到执行任务之后的返回结果
*/
class threadpool_v2{
private:
	threadsafe_queue<function_wrapper> m_WorkQueue;
	std::atomic_bool m_Done;
	std::vector<std::thread> m_Threads;

	void worker_thread() {
		while(!m_Done) {
			function_wrapper work;
			if (m_WorkQueue.try_pop(work)) {
				work();
			} else {
				std::this_thread::yield();
			}
		}
	}
public:
	template<typename FunctionType>
	std::future<typename std::result_of<FunctionType()>::type> submit(FunctionType func) {
		typedef typename std::result_of<FunctionType()>::type result_type;
		std::packaged_task<result_type()> task(std::move(func));
		std::future<result_type> result(task.get_future());
		m_WorkQueue.push(std::move(task));
		return result;
	}

	/**
	* 构造函数
	*/
	explicit threadpool_v2(int thread_count_ = 0)
		: m_Done(false) {
		unsigned int thread_count = 1;
		if (thread_count_ <= 0) {
			thread_count = std::thread::hardware_concurrency();
		} else {
			thread_count = thread_count_;
		}
		std::cout << __FUNCTION__ << " thread_count is " << thread_count << std::endl;
		try {
			for (unsigned i = 0; i < thread_count; ++i) { 
				m_Threads.push_back(std::thread(&threadpool_v2::worker_thread, this));
			}
		}
		catch (...) {
			m_Done = true;
			throw;
		}
	}

	/**
	* 析构函数
	*/
	~threadpool_v2() {
		m_Done = true;
		for (auto it = m_Threads.begin(); it != m_Threads.end(); ++it) {
			if ((*it).joinable()) {
				(*it).join();
			}
		}
		std::cout << __FUNCTION__ << " done." << std::endl;
	}
protected:
};


/**
 * 线程池实现,
 * 在上一版本线程池实现的基础上
 * 为每个线程增加自生的任务队列,
 * 
 * 只有当线程自身的任务队列为空的时候,才会从全局的任务队列中拿取新的任务
 * 这样避免当有很多个线程在执行的时候,在总的任务队列上的竞争 
 * 
 * 可能存在的问题:
 * 当任务分配不平衡时,可能导致一些线程的私有队列中存在大量任务,而其他线程却没有任务可以处理的情况.
 * ***/
class threadpool_v3{
private:
	//线程池的任务队列,所有线程共享的任务队列
	threadsafe_queue<function_wrapper> m_PoolWorkQueue;
	//线程自身的任务队列,线程独享的,不存在竞争,所有直接用系统提供的std::queue就可以
	typedef std::queue<function_wrapper> local_queue_type;
	//每个线程单独持有的任务队列
	static thread_local std::unique_ptr<local_queue_type> local_work_queue;
	std::atomic_bool m_Done;
	std::vector<std::thread> m_Threads;
	void worker_thread() {
		while(!m_Done) {
			run_pending_task();
		}
	}
public:
	void run_pending_task(){
		function_wrapper task;
		if (local_work_queue != nullptr &&
			!local_work_queue->empty()) {  //线程本地任务不为空,则直接取出一个任务直接执行
			task = std::move(local_work_queue->front());
			local_work_queue->pop();
			task();
		} else 
		if (m_PoolWorkQueue.try_pop(task)) {//直接从总的任务队列中取出一个任务进行执行
			task();
		} else {
			std::this_thread::yield();
		}
	}
	
	/**
	 * 提交任务到线程池中
	 * ****/
	template<typename FunctionType>
	std::future<typename std::result_of<FunctionType()>::type> submit(FunctionType func) {
		typedef typename std::result_of<FunctionType()>::type result_type;
		
		std::packaged_task<result_type()> task(std::move(func));
		std::future<result_type> result(task.get_future());
		//当前线程已经有任务队列,则直接将任务加入到本地线程的任务队列中
		if (local_work_queue != nullptr) {
			local_work_queue->push(std::move(task));
		} else {
			//否则就放入到总的任务队列中 
			m_PoolWorkQueue.push(std::move(task));
		}
		return result;
	}

	/**
	* 构造函数
	*/
	explicit threadpool_v3(int thread_count_ = 0)
		: m_Done(false) {
		unsigned int thread_count = 1;
		if (thread_count_ <= 0) {
			thread_count = std::thread::hardware_concurrency();
		} else {
			thread_count = thread_count_;
		}
		std::cout << __FUNCTION__ << " thread_count is " << thread_count << std::endl;
		try {
			for (unsigned i = 0; i < thread_count; ++i) { 
				m_Threads.push_back(std::thread(&threadpool_v3::worker_thread, this));
			}
		}
		catch (...) {
			m_Done = true;
			throw;
		}
	}

	/**
	* 析构函数
	*/
	~threadpool_v3() {
		m_Done = true;
		for (auto it = m_Threads.begin(); it != m_Threads.end(); ++it) {
			if ((*it).joinable()) {
				(*it).join();
			}
		}
		std::cout << __FUNCTION__ << " done." << std::endl;
	}
protected:
};
//thread_local 变量是类成员时,必须是static 的,并且必须作为static成员初始化
thread_local std::unique_ptr<std::queue<function_wrapper>> threadpool_v3::local_work_queue = std::unique_ptr<std::queue<function_wrapper>>();


/**
 * 允许任务窃取的基于锁的队列
 * 存储的元素默认是function_wrapper
 * 对于拥有此队列的线程而言, push, try_pop操作的都是队首,就是一个先进后出的栈
 * 对于其他线程而言, try_steal操作的是队尾,从而最小化竞争
 * ***/
template<typename T=function_wrapper>
class work_stealing_queue {
private:
	std::deque<T> the_queue;
	mutable std::mutex the_mutex;
public:
	//构造函数
	work_stealing_queue(){}

	//无拷贝构造函数
	work_stealing_queue(work_stealing_queue& other) = delete;

	//无拷贝赋值操作
	work_stealing_queue& operator=(work_stealing_queue& other) = delete;

	/****
	 * 队首入队列
	*/
	void push(T& data) {
		std::lock_guard<std::mutex> lock(the_mutex);
		the_queue.push_front(std::move(data));
	}
	/****
	 * 队首入队列
	*/
	void push(T&& data) {
		std::lock_guard<std::mutex> lock(the_mutex);
		the_queue.push_front(std::move(data));
	}
	/****
	 * 队列是否为空
	*/
	bool empty() const {
		std::lock_guard<std::mutex> lock(the_mutex);
		return the_queue.empty();
	}

	/***
	 * 从队首弹出数据项
	 * 如果队列为空,则返回false,有数据则返回true
	*/
	bool try_pop(T& out_data/*out*/) {
		std::lock_guard<std::mutex> lock(the_mutex);
		if (the_queue.empty()) {
			return false;
		}
		out_data = std::move(the_queue.front());
		the_queue.pop_front();
		return true;
	}

	/*****
	 * 从队尾弹出数据项
	 * 如果队列为空,则返回false
	 * 否则返回true
	*/
	bool try_steal(T& out_data/*out*/) {
		std::lock_guard<std::mutex> lock(the_mutex);
		if (the_queue.empty()) {
			return false;
		}
		out_data = std::move(the_queue.back());
		the_queue.pop_back();
		return true;
	}
protected:
};

class threadpool_v4{
private:
	typedef function_wrapper task_type; //任务类型
	//线程运行是否全部结束标志
	std::atomic_bool m_Done;
	//线程池总的任务队列,	
	threadsafe_queue<task_type> pool_task_queue;
	//每个线程都单独分配一个任务队列,类型是work_strealing_queue
	// std::vector<std::unique_ptr<work_stealing_queue<task_type>>> thread_queues;
	std::vector<std::shared_ptr<work_stealing_queue<task_type>>> thread_queues;
	//全部线程
	std::vector<std::thread> m_Threads;
	//线程启动运行的锁,防止任务队列还没有构造好,线程就已经启动了,对thread_queues的操作进行加锁
	SpinlockMutex start_mutex_;
	
	//线程本地任务队列的指针,指向thread_queues
	static thread_local std::shared_ptr<work_stealing_queue<task_type>> local_work_queue;
	//线程本地索引位,标识local_work_queue
	static thread_local int local_index;
	//线程数
	const unsigned int m_ThreadCount;

	//线程运行函数
	void work_thread(unsigned my_index_) {
		local_index = my_index_;
		start_mutex_.Lock();
		std::cout << __FUNCTION__ << ",my_index_ = " << my_index_ << " thread_queues.size = " << thread_queues.size() << std::endl;
		// local_work_queue = thread_queues[my_index_].get();
		local_work_queue = thread_queues.at(my_index_);
		start_mutex_.Unlock();

		while(!m_Done) {
			run_pending_task();
		}
		local_index = 0;
		local_work_queue = nullptr;
	}
	void run_pending_task(){
		task_type task;
		if (pop_task_from_local_queue(task) ||
			pop_task_from_pool_queue(task)  ||
			pop_task_from_other_thread_queue(task)
			) {
				task();
		} else {
			std::this_thread::yield();
		}
	}
	//从线程本地任务队列中取出任务
	bool pop_task_from_local_queue(task_type& task) {
		return local_work_queue && local_work_queue->try_pop(task);
	}
	//从线程池总的任务队列中取出任务
	bool pop_task_from_pool_queue(task_type& task) {
		return pool_task_queue.try_pop(task);
	}
	//从其他线程的任务队列中偷取任务
	bool pop_task_from_other_thread_queue(task_type& task) {
		for(unsigned i = 0; i< m_ThreadCount && i != local_index; ++i) {
			unsigned const index = (local_index + i + 1 ) % m_ThreadCount;
			if (thread_queues[index]->try_steal(task)) {
			// if (thread_queues[index]->try_pop(task)) {
				return true;
			}
		}
		return false;
	}
public:
	threadpool_v4(unsigned threads_count_ = 0) 
		: m_Done(false),
		m_ThreadCount(threads_count_ <=0 ? std::thread::hardware_concurrency() : threads_count_) {
		// std::cout << __FUNCTION__ << " called, threads_count is "  << threads_count << std::endl;
		try {
			start_mutex_.Lock();
			for(unsigned i = 0; i < m_ThreadCount; ++i) {
				thread_queues.push_back(std::make_shared<work_stealing_queue<task_type>>());
				m_Threads.push_back(std::thread(&threadpool_v4::work_thread, this, i));
			}
			start_mutex_.Unlock();
		}catch(...) {
			m_Done = true;
			throw;
		}
	}

	~threadpool_v4(){
		std::cout << __FUNCTION__ << " called" << std::endl;
		m_Done = true;
		for(unsigned i = 0; i< m_ThreadCount;++i) {
			if(m_Threads[i].joinable()) {
				m_Threads[i].join();
			}
		}
	}

	/******
	 * 提交任务给线程池执行,
	 * 并返回future从而获得任务执行的结果
	*/
	template<typename FunctionType>
	std::future<typename std::result_of<FunctionType()>::type> submit(FunctionType func) {
		typedef typename std::result_of<FunctionType()>::type result_type;

		std::packaged_task<result_type()> task(func);
		std::future<result_type> result(task.get_future());
		if (local_work_queue) {
			local_work_queue->push(std::move(task));
		} else {
			pool_task_queue.push(std::move(task));
		}
		return result;
	}

protected:
};

//为local_work_queue, local_index 设置初始值
thread_local std::shared_ptr<work_stealing_queue<function_wrapper>> threadpool_v4::local_work_queue = std::shared_ptr<work_stealing_queue<function_wrapper>>();
thread_local int threadpool_v4::local_index = 0;


//采用哪一版本的线程池
using threadpool = threadpool_v4;
