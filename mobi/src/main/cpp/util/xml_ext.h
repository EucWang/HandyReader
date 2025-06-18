//
// Created by wxn on 2025/6/18.
//

#ifndef UREAD_XML_EXT_H
#define UREAD_XML_EXT_H

#include <string>
#include "../util/log.h"
#include "tinyxml2.h"
#include "string_ext.h"
#include <stack>

class xml_ext {
public:
    static std::string getEleText(const tinyxml2::XMLElement *elem);

    static std::string getEleAttr(const tinyxml2::XMLElement *elem, const char *attr_name);

    static std::string getEleAttr(tinyxml2::XMLElement *elem, const char *attr_name);

    /***
     * 根据id查找节点, 返回body的子节点，该节点的有id属性，或者其子节点有id属性
     * @param elem
     * @param id
     * @return
     */
    static tinyxml2::XMLElement *findEleById(tinyxml2::XMLElement *elem, const char *id);

    static std::string getText(const tinyxml2::XMLElement *elem);

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
    static const tinyxml2::XMLElement* getChildByNameAndAttr(const tinyxml2::XMLElement *elem,
                                                      const std::string &child_name,
                                                      const std::string &attr_name,
                                                      const std::string &attr_value);
};

#endif //UREAD_XML_EXT_H
