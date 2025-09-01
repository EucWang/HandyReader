#pragma warning (disable:4819)
#pragma once
#include <exception>
#include <thread>
#include <iostream>
#include <stdexcept>
#include <stack>
#include <mutex>
#include <memory>

/**
* 栈为空异常
*/
class empty_stack : std::exception {
public:
	const char* what() const throw() override {
		return "empty stack";
	}
};

/**
* 线程安全的stack栈
*/
template<class T>
class threadsafe_stack {
public:
	/**
	* 默认构造函数
	*/
	explicit threadsafe_stack(){}
	/**
	* 复制构造函数
	*/
	explicit threadsafe_stack(const threadsafe_stack& other) {
		std::lock_guard<std::mutex> lock(other.m_Mutex);
		m_Data = other.m_Data;
	}
	/**
	* 禁用赋值运算符
	*/
	threadsafe_stack& operator=(const threadsafe_stack&) = delete;

	/**
	*  从栈顶压入一个元素
	*/
	void push(T new_value) {
		std::lock_guard<std::mutex> lock(m_Mutex);
		m_Data.push(new_value);
	}

	/**
	* 从栈顶拿取一个元素,如果栈空则抛出异常empty_stack,
	* 栈顶元素返回shared_ptr智能指针
	*/
	std::shared_ptr<T> pop() {
		std::lock_guard<std::mutex> lock(m_Mutex);
		if (m_Data.empty()) {
			throw empty_stack();
		}
		else {
			std::shared_ptr<T> const result(std::make_shared<T>(m_Data.top()));
			m_Data.pop();
			return result;
		}
	}

	/**
	* 从栈顶拿取一个元素,如果栈空则抛出异常empty_stack
	* 栈顶元素返回一个引用
	*/
	void pop(T& top_value) {
		std::lock_guard<std::mutex> lock(m_Mutex);
		if (m_Data.empty()) {
			throw empty_stack();
		}
		else {
			top_value = m_Data.top();
			m_Data.pop();
		}
	}

	bool empty() const {
		std::lock_guard<std::mutex> lock(m_Mutex);
		return m_Data.empty();
	}
private:
	std::stack<T> m_Data;
	mutable std::mutex m_Mutex;
protected:
};