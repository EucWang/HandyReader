//
// Created by MAC on 2025/6/18.
//

#ifndef U_READER2_TAG_INFO_H
#define U_READER2_TAG_INFO_H

#include <string>

/***
 * 对DocText的修饰，一些常见的css样式的简化
 */
typedef struct TagInfo_ {
    std::string uuid;       //唯一标识
    std::string anchor_id;         //作为锚点使用的id
    std::string name;       //名称，用于区分不同的修饰符类型
    size_t startPos;              //开始位置
    size_t endPos;                //结束位置

    std::string parent_uuid;    //父级uuid
    std::string params;           //字符串拼接的键值对，
} TagInfo;

#endif //U_READER2_TAG_INFO_H
