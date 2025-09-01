#pragma warning (disable:4819)
#pragma warning(disable:936)
#pragma once

#include <thread>
#include <exception>
#include <stdexcept>
#include <iostream>
#include "interrupt_thread.h"


/**
* 对自定义的可中断线程的守护，防止外部线程或者主线程结束时，而子线程没有结束导致的崩溃
*/
class scoped_interrupt_thread {
public:
	/****
	 * 构造函数
	*/
	explicit scoped_interrupt_thread(interrupt_thread& _thread) :
		m_Thread(std::move(_thread)) {
		if (!m_Thread.joinable()) {
			throw std::logic_error("No thread");
		}
	}
	/****
	 * 构造函数
	*/
	explicit scoped_interrupt_thread(interrupt_thread&& _thread) :
		m_Thread(std::move(_thread)) {
		if (!m_Thread.joinable()) {
			throw std::logic_error("No thread");
		}
	}

    template<typename FunctionType>
    explicit scoped_interrupt_thread(FunctionType& func): m_Thread(std::move(interrupt_thread(func))) {
        if (!m_Thread.joinable()) {
            throw std::logic_error("No thread");
        }
    }


	virtual ~scoped_interrupt_thread() {
		if (m_Thread.joinable()) {
#ifdef DEBUG
			std::cout << __FUNCTION__ << " join child thread waiting for it done." << std::endl;
#endif
			m_Thread.join();
		}
		else {
#ifdef DEBUG
			std::cout << __FUNCTION__ << " no need wait for child thread." << std::endl;
#endif
		}
	}

	void interrupt(){
		m_Thread.interrupt();
	}

	//禁止复制构造函数
	scoped_interrupt_thread(scoped_interrupt_thread const&) = delete;
	//禁止赋值运算符
	scoped_interrupt_thread& operator=(scoped_interrupt_thread const&) = delete;
private:
	interrupt_thread m_Thread;
protected:
};