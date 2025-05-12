//
// Created by wxn on 2024/7/17.
//

#ifndef MY_APPLICATION_SYNCHRONIZEDLIST_H
#define MY_APPLICATION_SYNCHRONIZEDLIST_H
#include <list>
#include <memory>
#include <mutex>

/***
 * 线程安全的单链表结构
 * @tparam T
 */
template<class T>
class SynchronizedList {

private:

    struct Node {
        std::mutex m_mutex;
        std::shared_ptr<T> m_data;
        std::unique_ptr<Node> m_next;

        explicit Node() : m_data(), m_next() {}
        explicit Node(T data) : m_data(make_shared<T>(data)), m_next() {}
    };

    /***
     * 头节点
     */
    Node head_;

    /***
     * 链表中的数据量
     */
    unsigned int size_;

public:
    /**
     * 默认构造函数
     */
    SynchronizedList() : size_(0) {}
    /**
     * 析构函数
     */
    ~SynchronizedList() {
        RemoveIf([](T const&){
            return true;
        });
    }

    /***
     * 禁止使用拷贝构造
     * @param other
     */
    SynchronizedList(SynchronizedList& other) = delete;
    /***
     * 禁止使用赋值构造
     * @param other
     */
    SynchronizedList& operator=(SynchronizedList& other) = delete;

    /***
     * 从头部插入数据项
     */
    void PushFront(T const& value) {
        //构造新节点
        std::unique_ptr<Node> new_node(value);
        //对节点头加锁
        std::lock_guard<std::mutex>(head_.m_mutex);
        //将旧的节点接到新节点后面
        new_node->m_next = std::move(head_.m_next);
         //将新节点接到head上
         head_.m_next = std::move(new_node);
    }

    /***
     * 对链表进行遍历
     * @tparam Function
     * @param f
     */
    template<typename Function>
    void ForEach(Function f) {
        Node* current = &head_;
        Node* next = nullptr;
        //对头节点加锁, 使用unique_lock
        //unique_lock的生命周期结束之后，它所管理的锁对象会被解锁。
        // unique_lock具有lock_guard的所有功能，而且更为灵活。
        std::unique_lock<std::mutex> lock(current->m_mutex);

        while((next = current->m_next.get()) != nullptr) {
            //遍历到下一个节点
            //对下一个节点加锁
            std::unique_lock<std::mutex> next_lock(next->m_mutex);
            //释放上一个锁
            lock.unlock();
            //执行遍历函数
            f(*(next->m_data));
            //将current指向next
            current = next;
            //将lock指向next_lock
            lock = std::move(next_lock);
        }
    }

    /***
     * 查找第一个满足条件的元素
     * @tparam Predicate
     * @param p
     * @return
     */
    template<typename Predicate>
    std::shared_ptr<T> FindFirstIf(Predicate p) {
        Node* current = &head_;
        Node* next = nullptr;
        //对current进行加锁
        std::unique_lock<std::mutex> lock(current->mMutex);
        while((next = current->m_next.get()) != nullptr) {
            //遍历到下一个节点
            //对下一个节点加锁
            std::unique_lock<std::mutex> next_lock(next->m_mutex);
            //释放上一个锁
            lock.unlock();

            //执行条件判断
            if(p(*(next->m_data))){
                return next->m_data;
            }
            //current -> next
            current = next;
            //lock -> next_lock
            lock = std::move(next_lock);
        }
    }

    /***
     * 删除满足条件的所有数据项
     * @tparam Predicate
     * @param p
     */
    template<typename Predicate>
    void RemoveIf(Predicate p) {
        Node* current = *head_;
        Node* next = nullptr;

        //lock current
        std::unique_lock<std::mutex> lock(current->m_mutex);
        while((next = current->m_next.get()) != nullptr) {
            //point to next
            //lock next
            std::unique_lock<std::mutex> next_lock(next->m_mutex);

            //predicate
            if (p(*(next->m_data)) == true) {
                //得到旧的节点
                std::unique_ptr<Node> old_next = std::move(current->m_next);
                //下下一个节点覆盖当前节点位置数据
                current->m_next = std::move(next->m_next);
                //数量--
                size_--;
                //释放当前锁
                next_lock.unlock();

                //release old_next
//                old_next.release();
            } else {
                //unlock preview
                lock.unlock();
                //current -> next
                current = next;
                //lock -> next_lock
                lock = std::move(next_lock);
            }
        }
    }

    /***
     * 从头部移除数据并返回
     * @return
     */
    std::shared_ptr<T> PopFront() {
        Node* current = &head_;
        Node* next = nullptr;
        //锁定当前head节点
        std::unique_lock<std::mutex> lock(current->m_mutex);
        //获取下一个节点
        next = current->m_next.get();

        if (next != nullptr) {
            //锁定下一个节点
            std::unique_lock<std::mutex> next_lock(next->m_mutex);
            //得到旧的节点
            std::unique_ptr<Node> old_node = std::move(current->m_next);
            //用下下一个节点覆盖next节点
            current->m_next = std::move(next->m_next);
            //数量--
            size_--;
            //释放锁
            next_lock.unlock();
            lock.unlock();
            return old_node->m_data;
        } else {
            //返回一个空的智能指针, 函数结束lock会自动释放
            return std::shared_ptr<T>();
        }
    }

    inline unsigned int size() { return size_; }

protected:

};

#endif //MY_APPLICATION_SYNCHRONIZEDLIST_H
