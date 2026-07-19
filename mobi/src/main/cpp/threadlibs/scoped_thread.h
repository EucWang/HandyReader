#pragma warning(disable : 4819)
#pragma warning(disable : 936)
#pragma once

#include <thread>
#include <exception>
#include <stdexcept>
#include <iostream>

//#include "../log.h"

// #define DEBUG

/**
 * 对线程的守护，防止外部线程或者主线程结束时，而子线程没有结束导致的崩溃
 */
class scoped_thread
{
public:
	explicit scoped_thread(std::thread _thread) : m_Thread(std::move(_thread))
	{
		if (!m_Thread.joinable())
		{
			throw std::logic_error("No thread");
		}
	}

	virtual ~scoped_thread()
	{
		if (m_Thread.joinable())
		{
//#ifdef DEBUG
//            LOGD("%s join parent child waiting for child thread done.\n", __func__);
//#endif
			m_Thread.join();
		}
		else
		{
//#ifdef DEBUG
//			std::cout << __FUNCTION__ << " no need wait for child thread.\n" << std::endl;
//            LOGD("%s no need wait fro child thread to finish.", __func__);
//#endif
		}
	}

	// 禁止复制构造函数
	scoped_thread(scoped_thread const &) = delete;
	// 禁止赋值运算符
	scoped_thread &operator=(scoped_thread const &) = delete;

private:
	std::thread m_Thread;

protected:
};
