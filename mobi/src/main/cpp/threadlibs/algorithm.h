#pragma warning (disable:4819)
#pragma once

#include <list>
#include <iostream>
#include <algorithm>
#include <future>
#include <memory>

/**
 * 采用list集合是形式的快速排序实现
*/
template<class T>
std::list<T> sequential_quick_sort(std::list<T> input){
   if (input.empty()) 
   {
        return input;
   }
    std::list<T> result;
    //从input移出第一个元素并将其放入到result集合中
    result.splice(result.begin(), input, input.begin());
    //第一个元素作为中轴分割数据
    T const& pivot = *result.begin();
    //使用函数std::partition来排序两段数据, 并返回分割的中点迭代器
    auto divide_point = std::partition(input.begin(), input.end(),
        [&](T const& t){return t < pivot;});
    std::list<T> lower_part;
    //将已经排序好的低于中点的全部移动到lower_part中,剩下的都是大于中点的
    lower_part.splice(lower_part.end(), input, input.begin(), divide_point);
    //递归掉用
    auto new_lower(
        sequential_quick_sort(std::move(lower_part))
    );
    auto new_higher(sequential_quick_sort(std::move(input)));
    //拼接两部分数据到result中然后返回集合
    result.splice(result.end(), new_higher);
    result.splice(result.begin(), new_lower);
    return result;
}
/**
 * 采用list集合是形式的快速排序实现
 * 采用async多线程方式来优化,
*/
template<class T>
std::list<T> parallel_quick_sort(std::list<T> input){
   if (input.empty()) 
   {
        return input;
   }
    std::list<T> result;
    //从input移出第一个元素并将其放入到result集合中
    result.splice(result.begin(), input, input.begin());
    //第一个元素作为中轴分割数据
    T const& pivot = *result.begin();
    //使用函数std::partition来排序两段数据, 并返回分割的中点迭代器
    auto divide_point = std::partition(input.begin(), input.end(),
        [&](T const& t){return t < pivot;});
    std::list<T> lower_part;
    //将已经排序好的低于中点的全部移动到lower_part中,剩下的都是大于中点的
    lower_part.splice(lower_part.end(), input, input.begin(), divide_point);
    //递归掉用
    // auto new_lower(
    //     sequential_quick_sort(std::move(lower_part))
    // );
    // std::future<T> new_lower(std::async(parallel_quick_sort(std::move(lower_part))));
    std::future<std::list<T>> new_lower = std::async(&parallel_quick_sort<T>, std::move(lower_part));
    auto new_higher(parallel_quick_sort(std::move(input)));
    //拼接两部分数据到result中然后返回集合
    result.splice(result.end(), new_higher);
    result.splice(result.begin(), new_lower.get());
    return result;
}