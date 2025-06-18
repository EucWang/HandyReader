//
// Created by wxn on 2025/6/18.
//
#include "xml_ext.h"

std::string xml_ext::getEleText(const tinyxml2::XMLElement *elem) {
    const char *elemText = elem->GetText();
    std::string text;
    if (elemText != nullptr && utf8Count(elemText) > 0) {
        text = elemText;
    }
    return text;
}

std::string xml_ext::getEleAttr(const tinyxml2::XMLElement *elem, const char *attr_name) {
    const char *attr = elem->Attribute(attr_name);
    std::string attr_value;
    if (attr != nullptr && strlen(attr) > 0) {
        attr_value = attr;
    }
    return attr_value;
}

std::string xml_ext::getEleAttr(tinyxml2::XMLElement *elem, const char *attr_name) {
    const char *attr = elem->Attribute(attr_name);
    std::string attr_value;
    if (attr != nullptr && strlen(attr) > 0) {
        attr_value = attr;
    }
    return attr_value;
}

/***
 * 根据id查找节点, 返回body的子节点，该节点的有id属性，或者其子节点有id属性
 * @param elem
 * @param id
 * @return
 */
tinyxml2::XMLElement *xml_ext::findEleById(tinyxml2::XMLElement *elem, const char *id) {
    auto start_time = std::chrono::high_resolution_clock::now();
    tinyxml2::XMLElement *item = nullptr;
    item = elem;
    std::stack<tinyxml2::XMLElement *> stack;
    bool flag = true;
    tinyxml2::XMLElement *target = nullptr;
    while (flag && item != nullptr) {
        std::string itemId = getEleAttr(item, "id");
        if (itemId == std::string(id)) {
            target = item;
            break;
        }

        //优先遍历子节点
        int childCount = item->ChildElementCount();
        if (childCount > 0) {
            stack.push(item);
            auto child = item->FirstChildElement();
            if (child != nullptr) {
                item = child;
            }
            continue;
        }
        //没有子节点，则遍历兄弟节点
        tinyxml2::XMLElement *bro = item->NextSiblingElement();
        if (bro != nullptr) {
            item = bro;
            continue;
        }

        //没有兄弟节点了，则这一层已经遍历完，从stack中弹出上一层的没有遍历完的节点，得到该节点的兄弟节点
        bro = nullptr;
        while (true) {
            if (stack.empty()) {//栈空，退出总循环
                flag = false;
                break;
            }
            tinyxml2::XMLElement *&top = stack.top();
            if (top == nullptr) { //stack空，退出循环
                flag = false;
                break;
            }
            stack.pop();
            bro = top->NextSiblingElement();    //，得到该节点的兄弟节点
            if (bro != nullptr) {   //该兄弟节点不为空，继续外层循环， 为空，则继续从栈顶拿结点
                break;
            }
        }
        if (flag && bro != nullptr) {
            item = bro;
        }
    }
    if (target == nullptr) {
        return nullptr;
    }
    tinyxml2::XMLElement *child = target;
    while (child != nullptr) {
        auto parent = child->Parent();
        if (parent == nullptr) {
            break;
        }
        auto parentItem = parent->ToElement();
        if (parentItem == nullptr) {
            break;
        }
        std::string parentName = parentItem->Name();
        if (parentName != "body") {
            child = parentItem;
            continue;
        }
        target = child;
        break;
    }
    auto end_time = std::chrono::high_resolution_clock::now();
    //输出结果统计信息(性能分析)
    auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(
            end_time - start_time).count();
    LOGD("%s: duration = %lld ms", __func__, duration);
    return target;
}

std::string xml_ext::getText(const tinyxml2::XMLElement *elem) {
    std::string output;
    if (elem != nullptr) {
        const char *text = elem->GetText();
        if (text != nullptr && strlen(text) > 0) {
            output = text;
        }
    }
    return output;
}

/***
 * 获得多个相同子元素的Text的拼接, 用,逗号拼接
 * @param elem
 * @param child_name
 * @return
 */
std::string
xml_ext::getChildrenTexts(const tinyxml2::XMLElement *elem, const std::string &child_name) {
    std::stringstream ss;
    if (elem != nullptr) {
        auto child = elem->FirstChildElement(child_name.c_str());
        bool first = true;
        while (child != nullptr) {
            if (!first) {
                ss << ", ";
            }
            ss << getText(child);
            child = child->NextSiblingElement(child_name.c_str());
            first = false;
        }
    }
    return ss.str();
}


/***
 * 查找子元素， 根据提供的子元素名，属性名/属性值
 * @param elem
 * @param child_name
 * @param attr_name
 * @param attr_value
 * @return
 */
const tinyxml2::XMLElement *xml_ext::getChildByNameAndAttr(const tinyxml2::XMLElement *elem,
                                                    const std::string &child_name,
                                                    const std::string &attr_name,
                                                    const std::string &attr_value) {
    const tinyxml2::XMLElement *target = nullptr;
    if (elem != nullptr) {
        const tinyxml2::XMLElement* child = elem->FirstChildElement(child_name.c_str());
        while (child != nullptr) {
            if (attr_value == getEleAttr(child, attr_name.c_str())) {
                target = child;
                break;
            }
            child = child->NextSiblingElement(child_name.c_str());
        }
    }
    return target;
}