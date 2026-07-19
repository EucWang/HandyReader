//
// Created by MAC on 2025/6/18.
//

#ifndef U_READER2_NAV_POINT_H
#define U_READER2_NAV_POINT_H

#include <string>

typedef struct NavPoint_ {
    std::string id;         //章节ID
    std::string parentId;   //上一章节id
    int playOrder = 0;      //顺序
    std::string text;       //章节名
    std::string src;        //章节开始位置
    int type = 0;           //章节类型， 默认0，正常支持的章节；
                            //          1：大章节自动切分的章节

    int splitSeq = -1;      //自动切分的章节在切分后的顺序

    bool operator<(const struct NavPoint_ &other) const { //排序用
        return playOrder < other.playOrder;
    }
} NavPoint;

#endif //U_READER2_NAV_POINT_H
