#pragma warning (disable:4819)
#pragma once
#include <memory>

template<class T>
class ThreadMsg {
private:
	int what;
	int arg1;
	int arg2;
	std::shared_ptr<T> obj;
public:
	/***
	 * 构造函数
	*/
	ThreadMsg(const int _what = 0,
		const int _arg1 = 0, 
		const int _arg2 = 0,
		std::shared_ptr<T> _obj = nullptr) :
		what(_what),
		arg1(_arg1),
		arg2(_arg2),
		obj(_obj) {}
	/***
	 * 拷贝构造
	*/
	ThreadMsg(const ThreadMsg& _msg):
		what(_msg.what),
		arg1(_msg.arg1),
		arg2(_msg.arg2),
		obj(_msg.obj) {
	};
	/**
	 * 赋值运算符
	 * **/
	void operator=(const ThreadMsg& _msg) {
		this->what = _msg.what;
		this->arg1 = _msg.arg1;
		this->arg2 = _msg.arg2;
		this->obj = _msg.obj;
	}
	/**
	* 移动构造
	*/
	ThreadMsg(ThreadMsg&& _msg) :
		what(_msg.what),
		arg1(_msg.arg1),
		arg2(_msg.arg2),
		obj(_msg.obj){
		_msg.obj = nullptr;
	}

	/***
	 * 析构函数
	*/
	~ThreadMsg() {
		obj = nullptr;
	}

	inline int getWhat() const { 
		return this->what;
	}

	inline int getArg1() {
		return this->arg1; 
	}
	inline int getArg2() {
		return this->arg2; 
	}
	inline std::shared_ptr<T> getObj() {
		return this->obj;
	}
};
