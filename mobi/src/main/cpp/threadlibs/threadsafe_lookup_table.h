#pragma warning (disable:4819)
#pragma once

#include <memory>
#include <iostream>
#include <unordered_map>
#include <list>
#include <map>
#include <shared_mutex>
#include <vector>
#include <iterator>
#include <algorithm>
#include <mutex>

template<typename Key, typename Value, typename Hash=std::hash<Key>>
class threadsafe_lookup_table {
private:
    class bucket_type{
        private:
            typedef std::pair<Key, Value> bucket_value;
            typedef std::list<bucket_value> bucket_data;
            typedef typename bucket_data::iterator bucket_iterator;
            typedef typename bucket_data::const_iterator bucket_const_iterator;

            bucket_data m_Data;                     //一个桶数据,就是一个list
            mutable std::shared_mutex m_Mutex;      //共享互斥元

            //根据key在桶中找到对应的位置的迭代器
            bucket_const_iterator find_entry_for(Key const& key) const {
                bucket_const_iterator result = std::find_if(m_Data.begin(), m_Data.end(),
                    [&](bucket_value const& item) {
                        return item.first == key;
                    }
                );
                return result;
            }

            /**
             * 将const_iterator 转换成 iterator
             * 注意这里的类型是: typename std::list<bucket_value>::const_iterator 
             * 注意这里的类型是: typename std::list<bucket_value>::iterator 
            */
            bucket_iterator convert_const_iterator_to_iterator(bucket_const_iterator _iterator){
                bucket_iterator result = m_Data.begin();
                std::advance(result, std::distance<bucket_const_iterator>(result, _iterator));
                return result;
            }
        public:
            /**
             * 根据key找到对应位置的Value值, 没有找到则返回default_value
            */
            Value value_for(Key const& key, Value const& default_value) const {
                std::shared_lock<std::shared_mutex> lock(m_Mutex);
                bucket_const_iterator it = find_entry_for(key);
                return (it == m_Data.end()) ? default_value : it->second;
            }

            /**
             * 添加或者更新键值对
            */
            void add_or_update_mapping(Key const& key, Value const& value) {
                std::unique_lock<std::shared_mutex> lock(m_Mutex);
                bucket_const_iterator it = find_entry_for(key);
                if (it == m_Data.end()) {
                    m_Data.push_back(bucket_value(key, value));
                } else {
                    typename std::list<bucket_value>::iterator item = convert_const_iterator_to_iterator(it);
                    item->second = value;
                }
            }

            /***
             * 移除键值对
            */
            void remove_mapping(Key const& key) {
                std::unique_lock<std::shared_mutex> lock(m_Mutex);
                bucket_const_iterator it = find_entry_for(m_Mutex);
                if (it != m_Data.end()) {
                    m_Data.erase(it);
                }
            }
    };

    //-----------------------------------------------------------------

    std::vector<std::shared_ptr<bucket_type>> buckets;  //用vector来保存全部的桶
    Hash hasher;

    /**
     * 根据key找到对应的桶
    */
    std::shared_ptr<bucket_type> get_bucket(Key const& key) const {
        std::size_t const bucket_index = hasher(key) % buckets.size();
        std::shared_ptr<bucket_type> item = buckets.at(bucket_index);
        return item;
    }
public:
    typedef Key key_type;
    typedef Value mapped_type;
    typedef Hash hash_type;

    /***
     * 构造函数 
    */
    threadsafe_lookup_table(
        unsigned num_buckets = 19,
        Hash const& hasher_ = Hash()):
        buckets(num_buckets), 
        hasher(hasher_) {

        //对桶进行初始化
        for(unsigned i = 0; i< num_buckets; i++)
        {
            buckets[i].reset(new bucket_type);
        }
    }

    //禁用拷贝构造函数
    threadsafe_lookup_table(const threadsafe_lookup_table* other) = delete;
    //禁用赋值运算符
    threadsafe_lookup_table& operator=(threadsafe_lookup_table const& other) = delete;

    /**
     * 根据key找到对应的value值
    */
    Value value_for(Key const& key, Value const& default_value = Value()) const {
        return get_bucket(key)->value_for(key, default_value);
    }

    /**
     * 添加或者更新键值对
    */
    void add_or_update_mapping(Key const& key, Value const& value) {
        get_bucket(key)->add_or_update_mapping(key, value);
    }

    /**
     * 移除键值对
    */
    void remove_mapping(Key const& key) {
        get_bucket(key)->remove_mapping(key);
    }

    /**
     * 从线程安全的查找表获得一份非线程安全的查找表的内存快照
    */
    std::map<Key,Value> get_map() const {
        //所有桶都得全部加锁
        std::vector<std::unique_lock<std::shared_mutex>> locks;
        for (int i = 0; i < buckets.size(); i++)
        {
            locks.push_back(std::unique_lock<std::shared_mutex>(buckets[i].m_Mutex));
        }
        std::map<Key, Value> result;
        for (size_t i = 0; i < buckets.size(); i++){
            for(auto it = buckets[i].m_Data.begin(); it != buckets[i].m_Data.end(); ++it) {
                result.insert(*it);    
            }
        }
        return result;
    }
protected:
};