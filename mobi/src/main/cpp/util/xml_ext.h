//
// Created by wxn on 2025/6/18.
//

#ifndef UREAD_XML_EXT_H
#define UREAD_XML_EXT_H

#include <string>
#include "../util/log.h"
#include "tinyxml2.h"
#include "string_ext.h"
#include "nav_point.h"
#include "tag_info.h"
#include <stack>

class xml_ext {
public:
//media_type
    static const std::string &MediaTypeHtml;  //html
    static const std::string &MediaTypeCss;                //css

    static const std::string &MediaTypeSvg;           //svg
    static const std::string &MediaTypeJpg;              //jpg/jpeg
    static const std::string &MediaTypeGif;               //gif
    static const std::string &MediaTypePng;               //png
    static const std::string &MediaTypeBmp;               //bmp

    static const std::string &MediaTypeOtf;  //font
    static const std::string &MediaTypeTtf; //字体

    static const std::string &MediaTypeMp3;             //mp3
    static const std::string &MediaTypeMpg;             //mp4

    static const std::string &MediaTypePdf;        //pdf

    static const std::string &MediaTypeOpf;  //opf
    static const std::string &MediaTypeNcx;       //ncx
    static const std::string &MediaTypeDat;            //data



    static std::string getEleText(const tinyxml2::XMLElement *elem);

    static std::string getEleAttr(const tinyxml2::XMLElement *elem, const char *attr_name);

    static std::string getEleAttr(tinyxml2::XMLElement *elem, const char *attr_name);

    static bool has_attr(tinyxml2::XMLElement *elem, const char *attr_name);

    static std::string get_img_src(tinyxml2::XMLElement *elem);

    /***
     * 根据id查找节点, 返回body的子节点，该节点的有id属性，或者其子节点有id属性
     * @param elem
     * @param id
     * @return
     */
    static tinyxml2::XMLElement *findEleById(tinyxml2::XMLElement *elem, const char *id);

    static std::string getText(const tinyxml2::XMLElement *elem);

    static std::string ele_name(const tinyxml2::XMLElement *elem);

    /***
     * 获得多个相同子元素的Text的拼接
     * @param elem
     * @param child_name
     * @return
     */
    static std::string getChildrenTexts(const tinyxml2::XMLElement *elem, const std::string &child_name);


    /***
     * 查找子元素， 根据提供的子元素名，属性名/属性值
     * @param elem
     * @param child_name
     * @param attr_name
     * @param attr_value
     * @return
     */
    static const tinyxml2::XMLElement *getChildByNameAndAttr(const tinyxml2::XMLElement *elem,
                                                             const std::string &child_name,
                                                             const std::string &attr_name,
                                                             const std::string &attr_value);

    static void parseNavData(tinyxml2::XMLElement *firstNavPoint, std::vector<NavPoint> &vectors, const char *parentId);

    static int parseNcxData(std::string &ncx_data, std::vector<NavPoint> &points);

    static bool has_child_img(tinyxml2::XMLElement *elem);

    /****
     * 将一个元素全部的属性拼接成一个字符串， 不同属性间用&拼接，属性名属性值间用=拼接 , 并处理href
     * @param elem 当前处理的dom元素
     * @param spineSrcName 所在的资源文件名
     * @return std::string
     */
    static std::string ele_params(const tinyxml2::XMLElement *elem, std::string &spineSrcName);


    /****
     * 用来判断一个节点，是否是一个自然段落
     * 1. 如果dom子元素首先就是文本内容，则返回true
     * 2. 如果dom子元素第一个节点不是文本内容， 则判断dom孩子节点是否是【img, p, div, blockquote】元素，不是则返回true
     * @param elem
     * @return
     */
    static bool is_paragraph(tinyxml2::XMLElement *elem);


    /****
     * 解析一个dom元素，将其全部文本合并，全部dom元素也合并，并处理startAnchorId， endAnchorId确定解析的开始/结束标记
     * @param elem [in] 解析的dom元素
     * @param fullText   [out]   合并的文本
     * @param parent_uuid   上一级的dom元素的唯一标识
     * @param initialOffset 初始的文本偏移位置
     * @param subTags        全部的dom元素对应的标签信息
     * @param startAnchorId  解析开始锚点
     * @param endAnchorId  解析结束锚点
     * @param flagAdd   允许解析的标记， 0:  未开始解析； 1: 正常解析中； 2: 解析结束
     * @param spineSrcName 当前的资源文件名
     * @return 加上新文本内容之后的文本偏移量
     */
    static size_t parse_elem(const tinyxml2::XMLElement *elem,
                             std::string &fullText,
                             std::string &parent_uuid,
                             size_t initialOffset,
                             std::vector<TagInfo> &subTags,
                             std::string &startAnchorId,
                             std::string &endAnchorId,
                             int *flagAdd,
                             std::string &spineSrcName);

    /****
     * 将一个dom元素的全部dom子元素内容解析成为一个自然段落
     * @param pElem     [in] 解析的dom元素
     * @param subTags [in,out]  全部的dom元素对应的标签信息
     * @param startAnchorId   解析开始锚点
     * @param endAnchorId   解析结束锚点
     * @param flagAdd   允许解析的标记， 0:  未开始解析； 1: 正常解析中； 2: 解析结束
     * @param spineSrcName   当前的资源文件名
     * @return 解析之后的纯文本
     */
    static std::string parse_paragraph(const tinyxml2::XMLElement *pElem,
                                       std::vector<TagInfo> &subTags,
                                       std::string &startAnchorId,
                                       std::string &endAnchorId,
                                       int *flagAdd,
                                       std::string &spineSrcName);
};

#endif //UREAD_XML_EXT_H
