#pragma once
#pragma warning(disable:936)

#include <memory>
#include <atomic>
#include "hp_owner.h"
#include "data_to_reclaim.h"

/**
 * 线程安全的无锁的栈结构
 * 基于风险指针的线程指针对结构来删除pop时的Node结构
 * 有问题的视线
*/
template<typename T>
class lock_free_stack0{
// private:
//     struct Node {
//         std::shared_ptr<T> m_Data;
//         Node* next;
//         Node(T const& data) : m_Data(std::make_shared<T>(data)), next(nullptr) {}
//     };

//     std::atomic<Node*> head; //头节点
//     std::atomic<unsigned> threads_in_pop;    //进入pop函数中的线程个数
//     std::atomic<Node*> to_be_deleted;        //等待被删除的Node的链表节点

//     //循环删除多个Node堆空间
//     static void delete_nodes(Node* nodes) {
//         while(nodes) {
//             Node* next = nodes->next;
//             delete nodes;
//             nodes = next;
//         }
//     }

//     /**
//      * 将to_be_deleted链表连接到last 的尾部,
//      * 然后将to_be_deleted赋值为first,即first成了to_be_deleted的头
//     */
//     void chain_pending_nodes(Node* first, Node* last) {
//         //* 将to_be_deleted链表连接到last 的尾部,
//         last->next = to_be_deleted;
//         // * 然后将to_be_deleted赋值为first,即first成了to_be_deleted的头
//         while(!to_be_deleted.compare_exchange_weak(
//             last->next,
//             first));
//     }

//     /**
//      * 找到nodes链表的末尾,然后将to_be_deleted链接到nodes的尾部
//      * 将nodes作为to_be_deleted的头部,
//      * 即整个ndoes都作为新的待删除链表
//     */
//     void chain_pending_nodes(Node* nodes) {
//         Node* last = nodes;
//         //循环遍历,直到nodes链表的尾部
//         while(Node* next = last->next) {
//             last = next;
//         } 
//         chain_pending_nodes(nodes, last);
//     }

//     /**
//      * 将一个节点添加到to_be_deleted待删除链表中
//     */
//     void chain_pending_node(Node* n) {
//         chain_pending_nodes(n, n);
//     }

//     void try_reclaim(Node* old_head){        //pop()函数中回收在队上分配的node空间
//         //当前就只有一个线程在执行pop函数
//         if (threads_in_pop == 1) {
//             //将to_be_deleted置为nullptr,并将旧址赋值给nodes_to_delete
//             Node* nodes_to_delete = to_be_deleted.exchange(nullptr);
//             if (!--threads_in_pop) {
//                 //删除已经存储到to_be_deleted中的待删除链表
//                 delete_nodes(nodes_to_delete);
//             } else if(nodes_to_delete) { //如果不为0,则表示还有其他线程在跑着
//                 chain_pending_nodes(nodes_to_delete);//将链表还是放回去
//             }
//             delete old_head;
//         }else { //多个线程在跑着,就将当前节点放入to_be_deleted中
//             chain_pending_node(old_head);
//             --threads_in_pop;
//         }
//     }
// public:
//     explicit lock_free_stack(){}
//     virtual ~lock_free_stack(){}
//     lock_free_stack(lock_free_stack const& other) = delete;
//     lock_free_stack& operator=(lock_free_stack const& other) = delete;

//     /**
//      * 向栈顶压入一个数据项
//     */
//     void push(T const& data) {
//         Node* new_node = new Node(data);
//         new_node->next = head.load();
//         //当前的head的值等于new_node->next的时候,表示没有其他线程修改这个值,
//         //则将head 的值更新成new_node
//         while(!head.compare_exchange_weak(new_node->next, new_node));
//     }

//     /***
//      * 从栈顶弹出一个数据项
//      * 此版本的pop, 通过持有一个待删除Node链表来确保不需要的Node节点能够被释放,从而避免内存泄露
//      * 如果在高负载的情况下, to_be_deleted链表很容易越界,从而再次导致内存泄露问题.
//      * 下一版本的pop采用 风险指针来避免内存泄露问题
//     */
//     std::shared_ptr<T> pop0() {
//         ++threads_in_pop;          //进入pop()函数自增,统计进入pop函数的线程数
    
//         Node* old_head = head.load();
//         //当前的head的值等于old_head表示没有其他线程修改这个值,则将head的值修改为old_head->next
//         while(old_head &&
//             !head.compare_exchange_weak(old_head, old_head->next));
//         //如果old_head为空则返回空的智能指针
//         // return old_head ? old_head->m_Data : std::shared_ptr<T>();

//         std::shared_ptr<T> result;
//         if (old_head) {
//             result.swap(old_head->m_Data); //将数据交给result 
//         }
//         //尝试回收old_head这个节点堆内存空间
//         try_reclaim(old_head);
//         return result;
//     }

//     /**
//      * 从栈顶弹出一个数据项
//      * 此版本的pop()函数,
//      * 通过thread_local 
//      * 风险指针链表
//      * 来确保正确的删除多余的Node节点,避免内存泄露
//      * 问题在于性能不高
//      * ***/
//     std::shared_ptr<T> pop(){
//         //获取当前线程的风险指针
//         std::atomic<void*>& hp = hp_owner::get_hazard_pointer_for_current_thread();

//         Node* old_head = head.load();    //
//         do {
//             Node* temp = nullptr;
//             do {
//                 temp = old_head;
//                 hp.store(old_head);       //将old_head存储为风险指针
//                 old_head = head.load();
//             }while(old_head != temp);     //确保没有其他线程在此期间执行,导致old_head变更
//         } while(old_head &&  //确保链表不为空
//             head.compare_exchange_strong(old_head, old_head->next)); //将head指向next节点

//         //已经完成了交换,则清理掉暂存的风险指针
//         hp.store(nullptr);

//         std::shared_ptr<T> result;
//         if (old_head) {
//             result.swap(old_head->m_Data);
//             //如果old_head依然有其他引用,则当次放入待删除队列中
//             if (hp_owner::outstanding_hazard_pointers_for(old_head)) {
//                 data_reclaim<Node>::instance()->reclaim_later(old_head);
//             } else {
//                 delete old_head;  //否则直接删除
//             }
//             //遍历待删除链表,如果没有和风险指针引用到的,则直接删除
//             data_reclaim<Node>::instance()->delete_nodes_with_no_hazards();
//         }
//     }
// protected:
};


/**
* 无锁实现的stack,基于引用计数
*/
template<typename T>
class lock_free_stack{
private:
    struct node;
    struct counted_node_ptr {
        int external_count;    //外部引用计数
        node* ptr;             //node结构指针
    };

    struct node {
        std::shared_ptr<T> data;             //存储的数据,共享指针类型
        std::atomic<int> internal_count;     //内部引用计数
        counted_node_ptr next;               //下一个节点
        //构造函数
        node(T const& data_): data(std::make_shared<T>(data_)), internal_count(0) {}
    };

    //头节点
    std::atomic<counted_node_ptr> head;

    /**
     * 增加头结点的外部引用计数
     **/
    void increase_head_count(counted_node_ptr& old_counter) {
        counted_node_ptr new_counter;
        do {
            new_counter = old_counter;
            ++new_counter.external_count;//自增操作,这个操作不是原子操作,do..while确保是操作的同一个操作数
        }while(!head.compare_exchange_strong(old_counter,
                                            new_counter,
                                          std::memory_order_acquire,
                                          std::memory_order_relaxed));
        old_counter.external_count = new_counter.external_count;
    }
public:
    /*
    * 析构函数
    **/
    ~lock_free_stack(){
        while(pop());
    }

    /**
     * 入栈操作
    */
    void push(T const& data_) {
        counted_node_ptr new_node;
        new_node.ptr = new node(data_);
        new_node.external_count = 1;
        new_node.ptr->next = head.load(std::memory_order_relaxed);
        while(!head.compare_exchange_weak(new_node.ptr->next, new_node,
                                            std::memory_order_release,
                                            std::memory_order_relaxed));
    }

    /***
     * 出栈操作
    */
    std::shared_ptr<T> pop(){
         counted_node_ptr old_head = head.load(std::memory_order_relaxed);
         // fo(;;) {
         while(true){
             increase_head_count(old_head); //增加old_head的外部引用计数
             node* const ptr = old_head.ptr;
             if (!ptr) { //ptr为空,则返回一个空智能指针
                 return std::shared_ptr<T>();
             }
             //让head指向下一个节点
             if (head.compare_exchange_strong(old_head,
                                             ptr->next,
                                             std::memory_order_relaxed)) {
                 std::shared_ptr<T> result;
                 result.swap(ptr->data); //获得输出的数据
                 int const count_increase = old_head.external_count - 2;
                 //
                 if (ptr->internal_count.fetch_add(count_increase,
                                                   std::memory_order_release) == -count_increase) {
                     delete ptr;                                    
                 }
                 return result;
             }else if (ptr->internal_count.fetch_add(-1, std::memory_order_relaxed) == 1){
                 ptr->internal_count.load(std::memory_order_acquire);
                 delete ptr;
             }
         }
        return std::shared_ptr<T>();
    }
protected:
};