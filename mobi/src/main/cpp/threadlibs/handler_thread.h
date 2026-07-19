#pragma warning (disable:4819)
#pragma once

#include "thread_msg.h"
#include "scoped_thread.h"
#include <memory>
#include <iostream>
#include <string>
#include <climits>
#include "threadsafe_queue.h"
//#include "../log.h"

template<class T>
class HandlerThread {
public:
    HandlerThread() : loop_flag_(true),
    has_stop_(false),
    scoped_thread_(nullptr){
//#ifdef DEBUG
//        LOGD("%s invoked", __PRETTY_FUNCTION__);
//#endif
    }

    virtual ~HandlerThread() {
        m_queue_.clear();
        delete scoped_thread_;
//#ifdef DEBUG
//        LOGD("%s invoked", __PRETTY_FUNCTION__);
//#endif
    }

    /***
     * 在start中调用_init
     */
    virtual void start() {
//        LOGD("%s invoked : thread#%ld", __PRETTY_FUNCTION__, std::this_thread::get_id());
        init_();
    }

    virtual void stop() {
//        LOGD("%s invoked : thread#%ld", __PRETTY_FUNCTION__ , std::this_thread::get_id());
        m_queue_.push(std::move(ThreadMsg<T>(MSG_WHAT_EXIT)));
    }

    virtual void sendMessage(ThreadMsg<T> &&msg) {
//#ifdef DEBUG
//        LOGD("%s invoked : thread#%ld, msg.what=%d", __PRETTY_FUNCTION__ , std::this_thread::get_id(), msg.getWhat());
//#endif
        if (!has_stop_) {
            m_queue_.push(std::move(msg));
        } else {
//#ifdef DEBUG
//            LOGD("%s invoke, has_stop_ is true, invoked failed", __PRETTY_FUNCTION__);
//#endif
        }
//        LOGD("%s invoke done", __func__ );
    }

    virtual void handleMessage(std::shared_ptr<ThreadMsg<T>> msg) {
//#ifdef DEBUG
//        LOGD("%s invoke :: msg[%d,%d,%d]", __PRETTY_FUNCTION__, msg->getWhat(), msg->getArg1(),
//             msg->getArg2());
//#endif
    };

    virtual bool beginThread(){
        return true;
    }
    virtual void endThread(){

    }
private:
protected:
    const int MSG_WHAT_EXIT = INT_MAX;

    /***
     * 重新调用start的时候，会调用init_函数，保证重新启动线程可以正常启动成功
     */
    void init_() {
        if (scoped_thread_ != nullptr) {
            delete scoped_thread_;
        }
        loop_flag_ = true;
        has_stop_ = false;
        scoped_thread_ = new scoped_thread(std::thread{
                &HandlerThread::thread_loop,
                this});
//#ifdef DEBUG
//        LOGD("%s invoked", __PRETTY_FUNCTION__);
//#endif
    }

    std::atomic_bool has_stop_;
    std::atomic_bool loop_flag_;

    threadsafe_queue<ThreadMsg<T>> m_queue_;
    scoped_thread* scoped_thread_;

    virtual void thread_loop() {
//#ifdef DEBUG
//        LOGD("%s invoked, m_LoopFlag is %d, thread#%ld", __PRETTY_FUNCTION__, loop_flag_.load(), std::this_thread::get_id());
//#endif
        bool ret = beginThread();
        if (!ret) {
//            LOGD("%s beginThread() is %d", __func__, ret);
            return;
        }

        while (loop_flag_) {
            std::shared_ptr<ThreadMsg<T>> message = m_queue_.wait_and_pop();
            if (message->getWhat() == MSG_WHAT_EXIT) {
//#ifdef DEBUG
//                LOGD("%s received exit message, exit thread.", __PRETTY_FUNCTION__);
//#endif
                has_stop_ = true;
                loop_flag_ = false;
                m_queue_.clear();
            } else {
                this->handleMessage(message);
            }
        }
        endThread();
    }
};