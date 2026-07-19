#pragma once
#pragma warning (disable:4819)

#include <memory>
#include <list>
#include <mutex>

template<typename T>
class threadsafe_list{
private:
    struct Node {
        std::mutex m_Mutex;          //每个节点都包含一个锁
        std::shared_ptr<T> m_Data;   //节点包含的数据
        std::unique_ptr<Node> m_Next;   //下一个节点的指针
        explicit Node(): m_Data(), m_Next(){}
        explicit Node(T const& _data): m_Data(std::make_shared<T>(_data)), m_Next() {}
    };

    Node head;           //头
    unsigned m_Size;
public:
    threadsafe_list():m_Size(0){}
    ~threadsafe_list(){
        remove_if([](T const&){return true; });
    }

    /**
     * 禁用拷贝构造函数和赋值运算符
    */
    threadsafe_list(threadsafe_list& other) = delete;
    threadsafe_list& operator=(threadsafe_list& other) = delete;

    /**
     * 从头部插入数据
    */
    void push_front(T const& value) {
        //构造新的节点
        std::unique_ptr<Node> new_node(new Node(value));
        //加锁
        std::lock_guard<std::mutex> lock(head.m_Mutex);
        //将头结点的next连接到新节点的next上
        new_node->m_Next = std::move(head.m_Next);
        //将新节点连接到头节点的next上
        head.m_Next = std::move(new_node);
        m_Size++;
    }

    /**
     * 遍历数据
    */
    template<typename Function>
    void for_each(Function f) {
        Node* current = &head;
        std::unique_lock<std::mutex> lock(head.m_Mutex);
        Node* next = nullptr;
        while((next = (current->m_Next).get()) != nullptr) {
            std::unique_lock<std::mutex> next_lock(next->m_Mutex); //锁定下一个节点的互斥元
            lock.unlock();  //释放上一个节点的互斥元

            f(*(next->m_Data)); //执行函数,将当前节点的数据传递过去
            current = next;     //指向下一个节点

            lock = std::move(next_lock); //当前锁指向下一个锁
        }
    }

    /**
     * 查找数据
    */
    template<typename Predicate>
    std::shared_ptr<T> find_first_if(Predicate p) {
        Node* current = &head;
        std::unique_lock<std::mutex> lock(head.m_Mutex);
        Node* next = nullptr;
        while((next = current->m_Next.get())) {
            std::unique_lock<std::mutex> next_lock(next->m_Mutex);
            lock.unlock();

            if (p(*(next->m_Data))) {
                return next->m_Data;
            }
            current = next;
            lock = std::move(next_lock);
        }
        return std::shared_ptr<T>();
    }

    /**
     * 删除数据
    */
    template<typename Predicate>
    void remove_if(Predicate p) {
        Node* current = &head;
        Node* next = nullptr;
        std::unique_lock<std::mutex> lock(head.m_Mutex);
        while((next = current->m_Next.get()) != nullptr) {
            std::unique_lock<std::mutex> next_lock(next->m_Mutex);
            if (p(*(next->m_Data)) == true) {
                std::unique_ptr<Node> old_next = std::move(current->m_Next); //清除一个元素之后并不会停止轮训
                current->m_Next = std::move(next->m_Next);
                m_Size--;
                next_lock.unlock();
            } else { //
                lock.unlock();
                current = next;
                lock = std::move(next_lock);
            }
        }
    }
    /**
     * 从头部移出数据
     * 非阻塞获取, 如果没有则返回空的智能指针
    */
    std::shared_ptr<T> pop_front() {
        Node* current = &head;
        std::unique_lock<std::mutex> lock(head.m_Mutex);
        Node* next = current->m_Next.get();
        if (next != nullptr) {
            std::unique_lock<std::mutex> next_lock(next->m_Mutex);
            std::unique_ptr<Node> old_next = std::move(current->m_Next);
            current->m_Next = std::move(next->m_Next);
            m_Size--;
            next_lock.unlock();
            lock.unlock();
            return old_next->m_Data;
        } else {
            return std::shared_ptr<T>();
        }
    }

    inline unsigned size() {return m_Size;}

    /**
     * 清空全部元素
    */
    void clear() {
        remove_if([&](T& item)->bool{return true;});
    }
protected:
};