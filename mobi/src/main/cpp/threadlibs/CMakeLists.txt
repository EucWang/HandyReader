# CMakeList.txt: commonlibs 的 CMake 项目，在此处包括源代码并定义
# 项目特定的逻辑。
#
cmake_minimum_required (VERSION 3.8)

project (threadlibs)

# 将源代码添加到此项目的可执行文件。
add_library(threadlibs
		SynchronizedList.h
		scoped_thread.h
		algorithm.h
		thread_msg.h
		threadsafe_list.h
		spinlock_mutex.h
		copy_exception.h
		threadsafe_stack.h
		threadsafe_queue.h
		threadsafe_lookup_table.h
		handler_thread.h
		hierarchical_mutex.h
		hierarchical_mutex.cpp
		threadsafe_lookup_table.h
		threadsafe_list.h
		lock_free_stack.h
		hp_owner.h
		data_to_reclaim.h
		threadpool.h
		interrupt_thread.h
		scoped_interrupt_thread.h
#        ../log.h
	)
