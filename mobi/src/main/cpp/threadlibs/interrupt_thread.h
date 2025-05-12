#pragma once

#include <thread>
#include <memory>
#include <iostream>
#include <atomic>
#include <mutex>
#include <future>
#include <exception>
#include <condition_variable>

template<typename Lockable>
struct custom_lock;

/**
 * interrupt_flag的前置声明
*/
class interrupt_flag;

/***
 * 默认的predicate函数
 * 用于interruptable_wait()的第三个参数
*/
extern bool interrupt_thread_default_predicate(){
	return false;
};

//------------------------------------------------------thread_interrupted
/**
 * 终端线程抛出的异常类
*/
class thread_interrupted : public std::exception{
    public:
    const char* what() const noexcept override {
        return "therad_interrupted";
    }
};

//------------------------------------------------------interrupt_flag
/****
 * 中断标志包装类
*/
// class interrupt_flag : public std::enable_shared_from_this<interrupt_flag> {
class interrupt_flag {
public:
private:
    std::atomic<bool> m_Flag;                           //原子bool变量
    std::condition_variable* m_Thread_Cond;             //线程条件变量
    std::condition_variable_any* m_Thread_Cond_Any;     //线程任意条件变量
    std::mutex m_Set_Clear_Mutex;                       //互斥元
    
    /****
     * 可中断的线程等待
    */
    template<typename Lockable>
    void wait(std::condition_variable_any& cv, 
                Lockable& _lock) {
        custom_lock clock(this, cv, _lock); //构造custom_lock,然后使用中断标志的互斥元加锁,此函数执行完成,则clock自动释放锁
        // custom_lock clock(shared_from_this(), cv, _lock); //构造custom_lock,然后使用中断标志的互斥元加锁,此函数执行完成,则clock自动释放锁
        interrupt_point();                  //检测中断点
        cv.wait(clock);                          //条件变量阻塞等待
        interrupt_point();                  //条件变量阻塞唤醒之后,马上检测中断点
    }
public:
    static thread_local interrupt_flag this_thread_interrupt_flag;
    //构造函数
    explicit interrupt_flag()
    :m_Flag(false), 
        m_Thread_Cond(nullptr),
        m_Thread_Cond_Any(nullptr) {
    }
    /****
     * 设置中断标记,并唤醒再线程条件变量上的阻塞
     */
    void set(){
        m_Flag.store(true, std::memory_order_relaxed);//设置原子变量为true
        //加锁,然后通知线程条件变量唤醒等待,以便走到中断点位置抛出异常
        std::lock_guard<std::mutex> lock(m_Set_Clear_Mutex);
        if (m_Thread_Cond) {
            m_Thread_Cond->notify_all();
        }
        if(m_Thread_Cond_Any) {
            m_Thread_Cond_Any->notify_all();
        }
    }
    /****
     * 保存线程的条件变量的指针到interrupt_flag中
    */
    void set_condition_variable(std::condition_variable& cv) {
        std::lock_guard<std::mutex> lock(m_Set_Clear_Mutex);
        m_Thread_Cond =&cv;
    }
    /***
     * 清理掉interrupt_flag中保存的线程的条件变量
    */
    void clear_condition_variable() {
        std::lock_guard<std::mutex> lock(m_Set_Clear_Mutex);
        m_Thread_Cond = nullptr;
    }
    /****
     * 是否设置了中断标记
     */
    bool is_set() const {
        return m_Flag.load(std::memory_order_relaxed);
    }
    /**
     * 在执行到中断点处,抛出异常,中断线程的执行
    */
    void interrupt_point(){
        if (this_thread_interrupt_flag.is_set()) {
            throw thread_interrupted();
        }
    }
    //------------------------------------------------------clear_cv_on_destruct
    /****
     * 在析构时,清理掉线程本地变量中保存的条件变量
     * 起到保护iterrupt_flag的作用
    */
    struct clear_cv_on_destruct {
        ~clear_cv_on_destruct(){
            this_thread_interrupt_flag.clear_condition_variable();
        }
    };

    /****
     * 可中断的线程等待
    */
    template<typename Predicate>
    void interruptable_wait(std::condition_variable& cv,
                            std::unique_lock<std::mutex>& lock,
                            Predicate pred)  {
        //检测中断点是否可以抛出异常,中断线程执行
        interrupt_point(); 
        //为线程本地变量设置条件变量
        this_thread_interrupt_flag.set_condition_variable(cv);
        //设置线程本地变量的条件变量的保护,当退出此函数时,将其设置为空
        interrupt_flag::clear_cv_on_destruct guard;
        //循环判断中断条件以及自定义条件,不满足则循环阻塞等待1ms
        //使用wait_for代替wait,方便后续设置等待时长
        while(!this_thread_interrupt_flag.is_set() && !pred()) {
            cv.wait_for(lock, std::chrono::milliseconds(1));
        }
        //再次对中断条件进行判断
        interrupt_point();
    }
    template<typename Lockable>
    void interruptable_wait(std::condition_variable_any& cv,
                            Lockable& _lock) {
        this_thread_interrupt_flag.wait(cv, _lock);
    }

    void clear_cv_any_and_unlock(){
        m_Thread_Cond_Any = nullptr;
        m_Set_Clear_Mutex.unlock();
    }
    void set_cv_any_and_lock(std::condition_variable_any& cond_){
        //让持有的interrupt_flag加锁
        m_Set_Clear_Mutex.lock();
        //并持有条件变量
        m_Thread_Cond_Any = &cond_;
    }
    void unlock(){
        m_Set_Clear_Mutex.unlock();
    }
    template<typename Lockable>
    void lock(Lockable& _lock){
        std::lock(m_Set_Clear_Mutex, _lock);
    }
    std::unique_lock<std::mutex> getLock() {
        return std::unique_lock<std::mutex>(m_Set_Clear_Mutex);
    }

protected:
};

//------------------------------------------------------custom_lock
//自定义锁包装类,配合condition_variable_any 使用
template<typename Lockable>
struct custom_lock{
private:
    //持有的中断标志
    interrupt_flag* m_Self;
    // std::shared_ptr<interrupt_flag> m_Self;
    //持有的自定义锁
    Lockable& m_Lock;
public:
    //构造函数,
    // 并让中断变量持有的锁加锁
    // custom_lock(std::shared_ptr<interrupt_flag> self_, 
    custom_lock(interrupt_flag* self_, 
                std::condition_variable_any& cond_,
                Lockable& lock_) 
    : m_Self(self_), m_Lock(lock_) {
        if (m_Self != nullptr) {       
            m_Self->set_cv_any_and_lock(cond_);
        }
    }
    /**
     * 利用构造时传入的Lockable进行加锁
    */
    void lock(){
        // std::lock(m_Self->m_Set_Clear_Mutex, m_Lock);
        this->m_Self->lock(m_Lock);
    }
    /****
     * 利用构造时传入的Lockable进行解锁
        * 并释放中断变量持有的锁
    */
    void unlock(){
        m_Lock.unlock();
        // this->m_Self->m_Set_Clear_Mutex.unlock(); 
        this->m_Self->unlock();
    }
    //析构函数, 并执行释放锁操作
    ~custom_lock(){
        if (m_Self != nullptr) {
            m_Self->clear_cv_any_and_unlock();
        }
        m_Self = nullptr;
    }
};

/***************************************************************interrupt_thread
 * 可中断的线程
*/
class interrupt_thread {
private:
    //内部运行的线程
    std::thread internal_thread;

    //线程本地的中断标志
    // static thread_local interrupt_flag this_thread_interrupt_flag;
    //中断标志,可中断线程类实例的中断标志
    interrupt_flag* m_Flag;
public:
    /**
     * 构造函数
    */
    template<typename FunctionType>
    interrupt_thread(FunctionType& func) {
        std::promise<interrupt_flag*> p;
        internal_thread = std::thread([func, &p] {
                p.set_value(&interrupt_flag::this_thread_interrupt_flag);
                try {
                    func();
                } catch(thread_interrupted const& err) {
                    std::cout << __FUNCTION__ << " catch error[" << err.what() << "]" << std::endl;
                }
            });
        m_Flag = p.get_future().get();
    }
    void interrupt() {
        if (m_Flag) {
            m_Flag->set();
        }
    }
    void join() {
        internal_thread.join();
    }
    void detach() {
        internal_thread.detach();
    }
    bool joinable() const {
        return internal_thread.joinable();
    }
	/***
	 * 在子线程中执行时,interrupte()函数会触发interrupte_point()处抛出异常
	*/
	static void interrupt_point(){
		interrupt_flag::this_thread_interrupt_flag.interrupt_point();
	}
	/***
	 * 在子线程中,运行可中断的阻塞
     * 这里如果需要唤醒在std::condition_variable上的等待,不能通过cv的notify_one/notify_all来唤醒,
     * 只能通过Predicate函数返回true来唤醒等待
     * 使用示例:
     *       std::atomic_bool predicate(false);
     *       //......
     *       std::condition_variable _cv;
     *       std::mutex _mutex;
     *       std::unique_lock<std::mutex> lock(_mutex);
     *       interrupt_thread::interruptable_wait(_cv, lock, []{return predicate.load();});
     *       lock.unlock();
     * // ...
     * // 通过修改predicate原子bool值来唤醒阻塞
     *      predicate.store(true);
     * 
     * @param cv   条件变量
     * @param lock 单独的互斥锁
     * @param pred 返回bool值的函数,作为是否终止阻塞的判断条件, 默认返回false表示阻塞,返回true表示不再阻塞
	*/
	template<typename Predicate>
	static void interruptable_wait(std::condition_variable& cv,
                                    std::unique_lock<std::mutex>& lock,
									Predicate pred){
		interrupt_flag::this_thread_interrupt_flag.interruptable_wait(cv, lock, pred);
	}
protected:
};

thread_local interrupt_flag interrupt_flag::this_thread_interrupt_flag = interrupt_flag();