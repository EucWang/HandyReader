//
// Created by wxn on 2025/6/18.
//
#include "xml_ext.h"

//media_type
const std::string &xml_ext::MediaTypeHtml = "application/xhtml+xml";  //html
const std::string &xml_ext::MediaTypeCss = "text/css";                //css

const std::string &xml_ext::MediaTypeSvg = "image/svg+xml";           //svg
const std::string &xml_ext::MediaTypeJpg = "image/jpeg";              //jpg/jpeg
const std::string &xml_ext::MediaTypeGif = "image/gif";               //gif
const std::string &xml_ext::MediaTypePng = "image/png";               //png
const std::string &xml_ext::MediaTypeBmp = "image/bmp";               //bmp

const std::string &xml_ext::MediaTypeOtf = "application/vnd.ms-opentype";  //font
const std::string &xml_ext::MediaTypeTtf = "application/x-font-truetype"; //字体

const std::string &xml_ext::MediaTypeMp3 = "audio/mpeg";             //mp3
const std::string &xml_ext::MediaTypeMpg = "video/mpeg";             //mp4

const std::string &xml_ext::MediaTypePdf = "application/pdf";        //pdf

const std::string &xml_ext::MediaTypeOpf = "application/oebps-package+xml";  //opf
const std::string &xml_ext::MediaTypeNcx = "application/x-dtbncx+xml";       //ncx
const std::string &xml_ext::MediaTypeDat = "application/unknown";            //data

std::string xml_ext::ele_name(const tinyxml2::XMLElement *elem) {
    std::string ret;
    if (elem != nullptr) {
        const char *name = elem->Name();
        if (name != nullptr && strlen(name) > 0) {
            ret = name;
        }
    }
    return ret;
}

std::string xml_ext::getEleText(const tinyxml2::XMLElement *elem) {
    std::string text;
    if (elem != nullptr) {
        const char *elemText = elem->GetText();
        if (elemText != nullptr && string_ext::utf8Count(elemText) > 0) {
            text = elemText;
        }
    }
    return text;
}

std::string xml_ext::getEleAttr(const tinyxml2::XMLNode *node, const char *attr_name) {
    std::string attr_value;
    if (node != nullptr && attr_name != nullptr) {
        auto ele = node->ToElement();
        if (ele != nullptr) {
            attr_value = getEleAttr(ele, attr_name);
        }
    }

    return attr_value;
}

std::string xml_ext::getEleAttr(const tinyxml2::XMLElement *elem, const char *attr_name) {
    std::string attr_value;
    if (elem != nullptr && attr_name != nullptr) {
        const char *attr = elem->Attribute(attr_name);
        if (attr != nullptr && strlen(attr) > 0) {
            attr_value = attr;
        }
    }
    return attr_value;
}

bool xml_ext::has_attr(tinyxml2::XMLElement *elem, const char *attr_name) {
    bool ret = false;
    if (elem != nullptr) {
        const char *attr = elem->Attribute(attr_name);
        if (attr != nullptr) {
            ret = true;
        }
    }
    return ret;
}


std::string xml_ext::get_img_src(tinyxml2::XMLElement *elem) {
    std::string imgSrc;
    if (elem != nullptr) {
        if (xml_ext::has_attr(elem, "src")) {
            imgSrc = xml_ext::getEleAttr(elem, "src");
        } else if (xml_ext::has_attr(elem, "xlink:href")) {
            imgSrc = xml_ext::getEleAttr(elem, "xlink:href");
        } else if (xml_ext::has_attr(elem, "href")) {
            imgSrc = xml_ext::getEleAttr(elem, "href");
        } else if (xml_ext::has_attr(elem, "l:href")) {
            imgSrc = xml_ext::getEleAttr(elem, "l:href");
        }
    }
    return imgSrc;
}

std::string xml_ext::getEleAttr(tinyxml2::XMLElement *elem, const char *attr_name) {
    std::string attr_value;
    if (elem != nullptr) {
        const char *attr = elem->Attribute(attr_name);
        if (attr != nullptr && strlen(attr) > 0) {
            attr_value = attr;
        }
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
    if (!xml_ext::is_paragraph_tag(ele_name(target))) {
        tinyxml2::XMLElement *child = target;
        while (child != nullptr) {
            if (xml_ext::is_paragraph_tag(ele_name(child))) {
                target = child;
                break;
            }
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
        const tinyxml2::XMLElement *child = elem->FirstChildElement(child_name.c_str());
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

void xml_ext::parseNavData(tinyxml2::XMLElement *firstNavPoint, std::vector<NavPoint> &vectors, const char *parentId) {
    for (tinyxml2::XMLElement *navPoint = firstNavPoint; navPoint; navPoint = navPoint->NextSiblingElement("navPoint")) {
        std::string id = xml_ext::getEleAttr(navPoint, "id");
        std::string playOrder = xml_ext::getEleAttr(navPoint, "playOrder");
        std::string label;
        xml_ext::get_ele_words(navPoint->FirstChildElement("navLabel"), label);
        std::string src = xml_ext::getEleAttr(navPoint->FirstChildElement("content"), "src");

        LOGD("%s::id[%s],playOrder[%s],label[%s],src[%s]", __func__, id.c_str(), playOrder.c_str(), label.c_str(), src.c_str());

        NavPoint nav;
        nav.id = id;
        nav.playOrder = string_ext::toInt(playOrder);
        nav.text = label;
        nav.src = src;
        nav.parentId = parentId;
        if (string_ext::startWith(nav.src, "../")) {
            nav.src = nav.src.substr(3);
        } else if (string_ext::startWith(nav.src, "./")) {
            nav.src = nav.src.substr(2);
        }
        vectors.push_back(nav);

        if (navPoint->ChildElementCount("navPoint") > 0) {
            parseNavData(navPoint->FirstChildElement("navPoint"), vectors, id.c_str());
        }
    }
}

int xml_ext::parseNcxData(std::string &ncx_data, std::vector<NavPoint> &points) {
    tinyxml2::XMLDocument doc;
    if (doc.Parse(ncx_data.c_str(), ncx_data.length()) != tinyxml2::XML_SUCCESS) {
        LOGE("%s failed to parse ncx", __func__);
        return 0;
    }

    tinyxml2::XMLElement *root = doc.RootElement();
    if (!root) {
        LOGE("%s failed parse ncx, no root element", __func__);
        return 0;
    }

    tinyxml2::XMLElement *navMapElem = root->FirstChildElement("navMap");
    if (!navMapElem) {
        LOGE("%s failed parse ncx, no navMap element", __func__);
        return 0;
    }
    tinyxml2::XMLElement *firstNavPoint = navMapElem->FirstChildElement("navPoint");
    parseNavData(firstNavPoint, points, "");
    std::sort(points.begin(), points.end());
    return 1;
}

/****
 * 直接子元素是否有img元素
 * @param elem
 * @return
 */
bool xml_ext::has_child_img(tinyxml2::XMLElement *elem) {
    bool ret = false;
    if (elem != nullptr) {
        auto child = elem->FirstChild();
        while (child != nullptr) {
            auto childElem = child->ToElement();
            if (childElem != nullptr) {
                std::string name = ele_name(childElem);
                if (!name.empty()) {
                    if (name == "img" || name == "image") {
                        ret = true;
                        break;
                    }
                }
            }
            child = child->NextSibling();
        }
    }
    return ret;
}


/****
 * 将一个元素全部的属性拼接成一个字符串， 不同属性间用&拼接，属性名属性值间用=拼接, 并处理href
 * @param elem
 * @param spineSrcName
 * @return
 */
std::string xml_ext::ele_params(const tinyxml2::XMLElement *elem, std::string &spineSrcName) {
    std::string params;
    if (elem != nullptr) {
        for (auto attri = elem->FirstAttribute(); attri != nullptr; attri = attri->Next()) {
            const char *attriName = attri->Name();
            const char *attriValue = attri->Value();
            if (attriName == nullptr || attriValue == nullptr) {
                continue;
            }
            std::string name(attriName, attriName + strlen(attriName));
            std::string value(attriValue, attriValue + strlen(attriValue));
            if (name.empty()) {
                continue;
            }

            std::string tagname = ele_name(elem);
            if (tagname == "img" || tagname == "image") {
                if (name == "src" || name == "xlink:href" ||
                    name == "href" || name == "l:href") {
                    name = "src";
                    if (string_ext::startWith(value, "../")) {
                        value = value.substr(3);
                    }
                    //防止路径中，存在url编码，用简单的url解码处理下
                    std::string decoded_value = string_ext::base_url_decode(value);
                    value = decoded_value;
                }
            }

            if (name == "href") {
                if (!value.empty()) {
                    if (!string_ext::startWith(value, "http")) {
                        bool isSrcName = true;
                        if (value.find('#') != std::string::npos) { //完整的链接
                        } else { //只包含一部分
                            if (string_ext::is_number(value) || string_ext::startWith(value, "#")) { // 只是anchor部分
                                isSrcName = false;
                            }
                        }

                        std::string href;
                        if (!isSrcName) {
                            href.append(spineSrcName);
                            if (!string_ext::startWith(value, "#")) {
                                href.append("#");
                            }
                        }
                        href.append(value);
                        value = href;
                    }
                }
            }
            if (!params.empty()) {
                params.append("&");
            }
            params.append(name).append("=").append(value);
        }
    }
    return params;
}


/****
 * 用来判断一个节点，是否是一个自然段落
 * 1. 如果dom子元素首先就是文本内容，则返回true
 * 2. 如果dom子元素第一个节点不是文本内容， 则判断dom孩子节点是否是【img, p, div, blockquote】元素，不是则返回true
 * @param elem
 * @return
 */
bool xml_ext::is_paragraph(tinyxml2::XMLElement *elem) {
    if (elem == nullptr) {
        return false;
    }
    auto child = elem->FirstChild();
    bool ret = false;
    while (child != nullptr) {
        if (child->ToText() != nullptr) {
            ret = true;
            break;
        } else if (child->ToElement() != nullptr) {
            auto childEle = child->ToElement();
            const char *name = childEle->Name();
            std::string nameStr(name, name + strlen(name));
            if (nameStr == "img" || nameStr == "image" || nameStr == "p" || nameStr == "div" || nameStr == "blockquote") {
                ret = false;
                break;
            }
        }
        child = child->NextSibling();
    }
    return ret;
}

size_t xml_ext::parse_elem(const tinyxml2::XMLElement *elem,
                           std::string &fullText,
                           std::string &parent_uuid,
                           size_t initialOffset,
                           std::vector<TagInfo> &subTags,
                           std::string &startAnchorId,
                           std::string &endAnchorId,
                           int *flagAdd,
                           std::string &spineSrcName) {
    size_t currentOffset = initialOffset;

    if (elem != nullptr) {
        for (const tinyxml2::XMLNode *child = elem->FirstChild(); child != nullptr; child = child->NextSibling()) {
            if (child->ToText()) {
                const char *text = child->Value();
                if (text != nullptr) {
                    fullText += text;
                    currentOffset += string_ext::utf8Count(text);
                }
            } else if (child->ToElement()) {
                auto item = child->ToElement();
                if (item != nullptr) {
                    size_t childStart = currentOffset;
                    std::string tagId = getEleAttr(item, "id");

                    if (!startAnchorId.empty() && startAnchorId == tagId) {
                        *flagAdd = 1;
                    } else if (!endAnchorId.empty() && endAnchorId == tagId) {
                        *flagAdd = 2;
                        break;
                    }

                    std::string params = xml_ext::ele_params(item, spineSrcName);
                    auto newTag = TagInfo{string_ext::generate_uuid(), tagId, item->Name(), currentOffset, currentOffset, parent_uuid, params};

                    size_t endOffset = parse_elem(item, fullText, newTag.uuid, childStart, subTags, startAnchorId, endAnchorId, flagAdd, spineSrcName);

                    if (endOffset >= childStart) {
                        newTag.endPos = endOffset;
                    }
                    currentOffset = endOffset;
                    subTags.push_back(newTag);
                }
            }
        }
    }
    return currentOffset;
}

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
std::string xml_ext::parse_paragraph(const tinyxml2::XMLElement *pElem,
                                     std::vector<TagInfo> &subTags,
                                     std::string &startAnchorId,
                                     std::string &endAnchorId,
                                     int *flagAdd,
                                     std::string &spineSrcName) {
    size_t offset = 0;
    std::string fullText;

    if (pElem != nullptr) {
        for (const tinyxml2::XMLNode *child = pElem->FirstChild(); child != nullptr; child = child->NextSibling()) {
            if (child->ToText()) {
                const char *text = child->Value();
                if (text != nullptr && string_ext::utf8Count(text) > 0) {
                    fullText += text;
                    offset += string_ext::utf8Count(text);
                }
            } else if (child->ToElement()) {
                size_t childStart = offset;

                auto elem = child->ToElement();
                const char *id = elem->Attribute("id");
                std::string aid;
                if (id != nullptr && strlen(id) > 0) {
                    aid = id;
                }
                if (!startAnchorId.empty() && startAnchorId == aid) {
                    *flagAdd = 1;
                } else if (!endAnchorId.empty() && endAnchorId == aid) {
                    *flagAdd = 2;
                    break;
                }

                std::string params = xml_ext::ele_params(elem, spineSrcName);

                auto newTag = TagInfo{string_ext::generate_uuid(), aid, elem->Name(), childStart, childStart, "", params};
                offset = xml_ext::parse_elem(child->ToElement(), fullText, newTag.uuid, childStart, subTags, startAnchorId, endAnchorId, flagAdd, spineSrcName);

                if (offset > childStart) {
                    newTag.endPos = offset;
                }
                subTags.push_back(newTag);
            }
        }
    }
    fullText = string_ext::cleanStr(fullText);
    return fullText;
}

/***
 * 是段落html标签
 * @param tag_name
 * @return
 */
bool xml_ext::is_paragraph_tag(const std::string &name) {
    if (name == "p" || name == "div" || name == "ol" || name == "li" ||
        name == "blockquote" || name == "h1" || name == "h2" || name == "h3" ||
        name == "h4" || name == "h5" || name == "h6" || name == "h7" ||
        //                           name == "img" || name == "image" ||
        name == "section" || name == "article" ||
        name == "table" || name == "tr" || name == "caption" ||
        name == "hr" || name == "br") {
        return true;
    }
    return false;
}

/***
 * 从tags中找到全部是cur_tags的全部父系节点
 * @param cur_tags
 * @param tags
 * @return
 */
std::vector<TagInfo> get_fathers_tags(const std::string &parent_uuid, const std::vector<TagInfo> &tags) {
    std::vector<TagInfo> ret_tags;
    if (tags.empty() || parent_uuid.empty()) {
        return ret_tags;
    }

    std::set<std::string> parents_uuid;
    parents_uuid.insert(parent_uuid);
    //先找到全部的父级uuid
    for (auto item = tags.rbegin(); item != tags.rend(); ++item) {
        std::vector<std::string> new_parents_uuid;
        for (auto &uuid: parents_uuid) {
            if ((*item).uuid == uuid && !((*item).parent_uuid.empty())) { //直接父级节点
                new_parents_uuid.push_back((*item).parent_uuid);
            }
        }
        if (!new_parents_uuid.empty()) {
            for (auto &uuid: new_parents_uuid) {
                parents_uuid.insert(uuid);
            }
        }
    }
    for (auto &item: tags) {
        if (parents_uuid.find(item.uuid) != parents_uuid.end()) {
            ret_tags.push_back(item);
        }
    }
    return ret_tags;
}

/***
 * 从tags中找到全部是cur_tags的全部父系节点，最终结果会合并cur_tags中的节点
 * @param cur_tags
 * @param tags
 * @return
 */
std::vector<TagInfo> get_fathers_tags(const std::vector<TagInfo> cur_tags, const std::vector<TagInfo> &tags) {
    std::vector<TagInfo> ret_tags;
    if (tags.empty() || cur_tags.empty()) {
        return ret_tags;
    }

    std::set<std::string> parents_uuid;
    for (auto &tag: cur_tags) {
        parents_uuid.insert(tag.parent_uuid);
    }
    //先找到全部的父级uuid
    for (auto item = tags.rbegin(); item != tags.rend(); ++item) {
        std::vector<std::string> new_parents_uuid;
        for (auto &uuid: parents_uuid) {
            if ((*item).uuid == uuid && !((*item).parent_uuid.empty())) { //直接父级节点
                new_parents_uuid.push_back((*item).parent_uuid);
            }
        }
        if (!new_parents_uuid.empty()) {
            for (auto &uuid: new_parents_uuid) {
                parents_uuid.insert(uuid);
            }
        }
    }
    for (auto &item: tags) {
        if (parents_uuid.find(item.uuid) != parents_uuid.end()) {
            ret_tags.push_back(item);
        }
    }
    ret_tags.insert(ret_tags.end(), cur_tags.begin(), cur_tags.end());
    return ret_tags;
}

/***
 * 判断tags中有没有不属于当前节点的父/祖父节点的节点, 用来确定是否需要将其生成一个自然段落
 * @param uuid 当前节点的父节点的uuid
 * @param tags 全部tags
 * @return 判断tags中有没有不属于当前节点的父/祖父节点的节点
 */
std::vector<TagInfo> non_father_tags(const std::string parent_uuid, const std::vector<TagInfo> &tags) {
    std::vector<TagInfo> ret_tags;
    if (tags.empty()) {
        return ret_tags;
    }

    if (!parent_uuid.empty()) {
        std::set<std::string> parents_uuid;
        parents_uuid.insert(parent_uuid);
        //先找到全部的父级uuid
        for (auto item = tags.rbegin(); item != tags.rend(); ++item) {
            std::vector<std::string> new_parents_uuid;
            for (auto &uuid: parents_uuid) {
                if ((*item).uuid == uuid && !((*item).parent_uuid.empty())) { //直接父级节点
                    new_parents_uuid.push_back((*item).parent_uuid);
                }
            }
            if (!new_parents_uuid.empty()) {
                for (auto &uuid: new_parents_uuid) {
                    parents_uuid.insert(uuid);
                }
            }
        }
        //再此遍历
        for (auto &item: tags) {
            bool inner = false;
            for (auto uuid: parents_uuid) {
                if (item.uuid == uuid) {
                    inner = true;
                    break;
                }
            }
            if (!inner) {
                ret_tags.push_back(item);
            }
        }
    } else {
        if (!tags.empty()) {
            for (auto &item: tags) {
                ret_tags.push_back(item);
            }
        }
    }
    return ret_tags;
}

/***
 * 计算一个标签下的全部子元素中所有字数和图片数
 * @param element
 * @param wordcount
 * @param piccount
 * @return
 */
size_t xml_ext::count_ele_words(tinyxml2::XMLElement *element, size_t *wordcount, size_t *piccount) {
    if (element == nullptr) {
        return 0;
    }
    tinyxml2::XMLNode *item = element->FirstChild();
    std::list<tinyxml2::XMLNode *> stack;
    bool flag = true;

    while (flag && item != nullptr) {
        std::string itemId = xml_ext::getEleAttr(item, "id");

        tinyxml2::XMLElement *domElem = item->ToElement();
        auto domText = item->ToText();
        if (domText != nullptr) {  //是文本节点, 文本节点的标签都在stack中
            const char *text = domText->Value();
            if (text != nullptr && strlen(text) > 0) {
                std::string str(text);
                str = string_ext::cleanStr(str);
                *wordcount += string_ext::utf8Count(str);
            }
        } else if (domElem != nullptr) {
            std::string name = xml_ext::ele_name(domElem);
            if (name == "img" || name == "image") {  //一个图片占100个
                *piccount += 1;
            }
        }

        //优先遍历子节点
        auto child = item->FirstChild();
        if (child != nullptr && domElem != nullptr) { //当前节点是Element，并且孩子节点不为空，则深入下一层
            stack.push_back(domElem);
            item = child;
            continue;
        }

        //没有子节点，则遍历兄弟节点
        auto bro = item->NextSibling();
        if (bro != nullptr) {
            item = bro;
            continue;
        }

        //没有兄弟节点，则这一层已经遍历完，从stack中弹出上一层的没有遍历完的节点，得到该节点的兄弟节点
        bro = nullptr;
        while (true) {
            if (stack.empty()) {
                flag = false;
                break;
            }
            tinyxml2::XMLNode *lastNode = stack.back();
            stack.pop_back();

            if (lastNode == nullptr) {
                flag = false;
                break;
            }

            auto node = lastNode;

            bro = lastNode->NextSibling();
            if (bro != nullptr) {           // 该兄弟节点不为空，继续外层循环， 为空，则继续从栈顶拿结点
                break;
            }
        }

        //遍历上/上上级的兄弟节点
        if (flag && bro != nullptr) {
            item = bro;
        }
    }

    return 1;
}

/***
 * 得到一个标签下以及其全部子标签下的所有文字
 * @param element
 * @param wordcount
 * @param piccount
 * @return
 */
size_t xml_ext::get_ele_words(tinyxml2::XMLElement *element, std::string &output) {
    int count = 0;
    if (element == nullptr) {
        return 0;
    }

    tinyxml2::XMLNode *item = element->FirstChild();
    std::list<tinyxml2::XMLNode *> stack;
    bool flag = true;

    while (flag && item != nullptr) {
        std::string itemId = xml_ext::getEleAttr(item, "id");

        tinyxml2::XMLElement *domElem = item->ToElement();
        auto domText = item->ToText();
        if (domText != nullptr) {  //是文本节点, 文本节点的标签都在stack中
            const char *text = domText->Value();
            if (text != nullptr && strlen(text) > 0) {
                std::string str(text);
                str = string_ext::cleanStr(str);
//                *wordcount += string_ext::utf8Count(str);
                count += string_ext::utf8Count(str);
                output.append(str);
            }
//        } else if (domElem != nullptr) {
//            std::string name = xml_ext::ele_name(domElem);
//            if (name == "img" || name == "image") {  //一个图片占100个
//                *piccount += 1;
//            }
        }

        //优先遍历子节点
        auto child = item->FirstChild();
        if (child != nullptr && domElem != nullptr) { //当前节点是Element，并且孩子节点不为空，则深入下一层
            stack.push_back(domElem);
            item = child;
            continue;
        }

        //没有子节点，则遍历兄弟节点
        auto bro = item->NextSibling();
        if (bro != nullptr) {
            item = bro;
            continue;
        }

        //没有兄弟节点，则这一层已经遍历完，从stack中弹出上一层的没有遍历完的节点，得到该节点的兄弟节点
        bro = nullptr;
        while (true) {
            if (stack.empty()) {
                flag = false;
                break;
            }
            tinyxml2::XMLNode *lastNode = stack.back();
            stack.pop_back();

            if (lastNode == nullptr) {
                flag = false;
                break;
            }

            auto node = lastNode;

            bro = lastNode->NextSibling();
            if (bro != nullptr) {           // 该兄弟节点不为空，继续外层循环， 为空，则继续从栈顶拿结点
                break;
            }
        }

        //遍历上/上上级的兄弟节点
        if (flag && bro != nullptr) {
            item = bro;
        }
    }

    return count;
}

void handle_table_cell_index(tinyxml2::XMLElement *ele_first_tr, int count_th, int count_td, int *index_row, std::vector<size_t> &col_words) {
    tinyxml2::XMLElement *ele_tr = ele_first_tr;
    std::string cell_name;
    if (count_th > 0) {
        cell_name = "th";
    } else if (count_td > 0) {
        cell_name = "td";
    }
    while (ele_tr != nullptr) {
        if (!cell_name.empty()) {
            auto ele_cell = ele_tr->FirstChildElement(cell_name.c_str());
            int index_col = 0;  //列索引
            while (ele_cell != nullptr) {
                size_t words = 0;
                size_t pics = 0;
                xml_ext::count_ele_words(ele_cell, &words, &pics);
                if (index_col >= col_words.size()) {
                    col_words.push_back(words + pics);
                } else {
                    col_words[index_col] = col_words[index_col] + words + pics;
                }

                ele_cell->SetAttribute("index", index_col);
                ele_cell = ele_cell->NextSiblingElement(cell_name.c_str());
                index_col++;
            }
        }

        ele_tr->SetAttribute("index", *index_row);
        ele_tr = ele_tr->NextSiblingElement("tr");
        (*index_row)++;
    }
}

/***
 * 解析一个表格的行，列数, 以及每一个tr，td的索引值，
 * table标签上插入rows, cols 记录行数列数
 * tr，td 标签上插入index， 记录行索引，列索引
 * @param element
 * @param row
 * @param col
 * @return
 */
size_t handle_table_attr(tinyxml2::XMLElement *ele_table) {
    if (ele_table == nullptr) {
        return 0;
    }
    tinyxml2::XMLNode *item = ele_table->FirstChild();
    std::list<tinyxml2::XMLNode *> stack;
    bool flag = true;
    int table_rows = 0; //多少行
    int table_cols = 0; //多少列
    int index_row = 0;  //行索引
    std::vector<size_t> cols_words;

    int count_thead = ele_table->ChildElementCount("thead");
    if (count_thead > 0) {
        auto ele_thead = ele_table->FirstChildElement("thead");
        table_rows += ele_thead->ChildElementCount("tr");

        auto ele_first_tr = ele_thead->FirstChildElement("tr");
        if (ele_first_tr != nullptr) {
            int count_th = ele_first_tr->ChildElementCount("th");
            if (count_th > 0 && count_th > table_cols) {
                table_cols = count_th;
            }
            int count_td = ele_first_tr->ChildElementCount("td");
            if (count_td > 0 && count_td > table_cols) {
                table_cols = count_td;
            }

            handle_table_cell_index(ele_first_tr, count_th, count_td, &index_row, cols_words);
        }
    }

    int count_tbody = ele_table->ChildElementCount("tbody");
    if (count_tbody > 0) {
        auto ele_tbody = ele_table->FirstChildElement("tbody");
        table_rows += ele_tbody->ChildElementCount("tr");

        auto ele_first_tr = ele_tbody->FirstChildElement("tr");
        if (ele_first_tr != nullptr) {
            int count_th = ele_first_tr->ChildElementCount("th");
            if (count_th > 0 && count_th > table_cols) {
                table_cols = count_th;
            }
            int count_td = ele_first_tr->ChildElementCount("td");
            if (count_td > 0 && count_td > table_cols) {
                table_cols = count_td;
            }

            handle_table_cell_index(ele_first_tr, count_th, count_td, &index_row, cols_words);
        }
    } else {
        int count_top_tr = ele_table->ChildElementCount("tr");
        if (count_top_tr > 0) {
            table_rows += count_top_tr;

            auto ele_first_tr = ele_table->FirstChildElement("tr");
            if (ele_first_tr != nullptr) {
                int count_th = ele_first_tr->ChildElementCount("th");
                if (count_th > 0 && count_th > table_cols) {
                    table_cols = count_th;
                }
                int count_td = ele_first_tr->ChildElementCount("td");
                if (count_td > 0 && count_td > table_cols) {
                    table_cols = count_td;
                }

                handle_table_cell_index(ele_first_tr, count_th, count_td, &index_row, cols_words);
            }
        }
    }

    int count_tfoot = ele_table->ChildElementCount("tfoot");
    if (count_tfoot > 0) {
        auto ele_tfoot = ele_table->FirstChildElement("tfoot");
        table_rows += ele_tfoot->ChildElementCount("tr");

        auto ele_first_tr = ele_tfoot->FirstChildElement("tr");
        if (ele_first_tr != nullptr) {
            int count_th = ele_first_tr->ChildElementCount("th");
            if (count_th > 0 && count_th > table_cols) {
                table_cols = count_th;
            }
            int count_td = ele_first_tr->ChildElementCount("td");
            if (count_td > 0 && count_td > table_cols) {
                table_cols = count_td;
            }

            handle_table_cell_index(ele_first_tr, count_th, count_td, &index_row, cols_words);
        }
    }
    if (table_cols > 0 && table_rows > 0) {
        ele_table->SetAttribute("cols", table_cols);
        ele_table->SetAttribute("rows", table_rows);

        float min_percent;    //最小百分比
        if (table_cols <= 5) {
            min_percent = 0.2f;
        } else if (table_cols > 5 && table_cols <= 10) {
            min_percent = 0.1f;
        } else if (table_cols > 10) {
            min_percent = 1.0f / table_cols;
        }
        size_t in_table_words = 0;
        for (auto &words: cols_words) {
            in_table_words += words;
        }
        if (in_table_words > 0 && !cols_words.empty()) {
            float left_percent = 1.0;
            std::vector<float> percents;
            for (int i = 0; i < cols_words.size(); ++i) {
                size_t words = cols_words[i];
                float min_left_percent = min_percent * (cols_words.size() - i - 1);
                float col_percent = (words * 1.0f) / in_table_words;
                if (col_percent <= min_percent) {
                    col_percent = min_percent;
                } else if (col_percent >= (left_percent - min_left_percent)) {
                    col_percent = left_percent - min_left_percent;
                }
                percents.push_back(col_percent);
                left_percent -= col_percent;
            }
            float calced_total = 0.0f;
            for (auto &percent: percents) {
                calced_total += percent;
            }
            float diff = (1.0f - calced_total) / percents.size(); //计算是否不够100%或者超过100%，将差值从每一项中排除掉
            if (diff != 0.0f) {
                for (int i = 0; i < percents.size(); ++i) {
                    percents[i] = percents[i] + diff;
                }
            }
            std::stringstream ss;
            for (auto &percent: percents) {
                if (!ss.str().empty()) {
                    ss << ";";
                }
                ss << int(percent * 100) << "%";
            }
            std::string table_percents = ss.str();
            ele_table->SetAttribute("table_percent", table_percents.c_str());
            LOGD("%s table cols[%d] rows[%d] percents[%s]", __func__, table_cols, table_rows, table_percents.c_str());
        }
    }

    return 1;
}


size_t xml_ext::count_words(
        tinyxml2::XMLElement *element,
        const std::string &startAnchorId,
        const std::string &endAnchorId,
        int *flagAdd,
        size_t *wordcount,
        size_t *piccount,
        volatile bool *run_flag) {
    if (element == nullptr) {
        return 0;
    }
    tinyxml2::XMLNode *item = element;
    std::list<tinyxml2::XMLNode *> stack;
    bool flag = true;

    while (flag && item != nullptr) {
        if (!(*run_flag)) {
            break;
        }
        std::string itemId = xml_ext::getEleAttr(item, "id");
        if (!endAnchorId.empty() && itemId == endAnchorId) {
            flag = false;
            *flagAdd = 2;
            break;
        } else if (!startAnchorId.empty() && itemId == startAnchorId) {
            *flagAdd = 1;
        }

        tinyxml2::XMLElement *domElem = item->ToElement();
        if (*flagAdd == 1) {
            auto domText = item->ToText();
            if (domText != nullptr) {  //是文本节点, 文本节点的标签都在stack中
                const char *text = domText->Value();
                if (text != nullptr && strlen(text) > 0) {
                    std::string str(text);
                    str = string_ext::cleanStr(str);
                    *wordcount += string_ext::utf8Count(str);
                }
            } else if (domElem != nullptr) {
                std::string name = xml_ext::ele_name(domElem);
                if (name == "img" || name == "image") {  //一个图片占100个
                    *piccount += 1;
                }
            }
        }

        //优先遍历子节点
        auto child = item->FirstChild();
        if (child != nullptr && domElem != nullptr) { //当前节点是Element，并且孩子节点不为空，则深入下一层
            stack.push_back(domElem);
            item = child;
            continue;
        }

        //没有子节点，则遍历兄弟节点
        auto bro = item->NextSibling();
        if (bro != nullptr) {
            item = bro;
            continue;
        }

        //没有兄弟节点，则这一层已经遍历完，从stack中弹出上一层的没有遍历完的节点，得到该节点的兄弟节点
        bro = nullptr;
        while (true) {
            if (stack.empty()) {
                flag = false;
                break;
            }
            tinyxml2::XMLNode *lastNode = stack.back();
            stack.pop_back();

            if (lastNode == nullptr) {
                flag = false;
                break;
            }

            auto node = lastNode;

            bro = lastNode->NextSibling();
            if (bro != nullptr) {           // 该兄弟节点不为空，继续外层循环， 为空，则继续从栈顶拿结点
                break;
            }
        }

        //遍历上/上上级的兄弟节点
        if (flag && bro != nullptr) {
            item = bro;
        }
    }

    return 1;
}

/****
 * 在一个资源文件汇总， 根据 anchors 来确定开始位置和结束位置，统计多个章节的字数
 * @param element  DOM开始扫描的节点
 * @param anchors  每个章节开始的锚点位置， 对应的是元素的id属性
 * @param wordCounts 每个章节的字数
 * @return 总字数
 */
size_t xml_ext::count_words(
        tinyxml2::XMLElement *element,
        const std::vector<std::string> &anchors,
        std::vector<std::pair<size_t, size_t>> &wordCounts,
        volatile bool *run_flag) {
    int total = 0;
    int chapterIndex = 0;
    size_t chapterWordCount = 0;
    size_t chapterPicCount = 0;

    if (element == nullptr) {
        return total;
    }
    tinyxml2::XMLNode *item = element;
    std::list<tinyxml2::XMLNode *> stack;
    bool flag = true;

    while (flag && item != nullptr) {
        if (!(*run_flag)) {
            break;
        }
        if (chapterIndex > anchors.size()) {
            break;
        }
        std::string itemId = xml_ext::getEleAttr(item, "id");
        if (chapterIndex < anchors.size() - 1) {
            if (itemId == anchors[chapterIndex + 1]) { //is next anchor
                wordCounts.emplace_back(std::pair<size_t, size_t>(chapterWordCount, chapterPicCount));  //save count to vector
                chapterIndex++;
                chapterWordCount = 0;
                chapterPicCount = 0;
            }
        }

        auto domText = item->ToText();
        tinyxml2::XMLElement *domElem = item->ToElement();
        if (domText != nullptr) {  //是文本节点, 文本节点的标签都在stack中
            const char *text = domText->Value();
            if (text != nullptr && strlen(text) > 0) {
                std::string str(text);
                str = string_ext::cleanStr(str);
                size_t count = string_ext::utf8Count(str);
                chapterWordCount += count;
                total += count;
            }
        } else if (domElem != nullptr) {
            std::string name = ele_name(domElem);
            if (name == "img" || name == "image") {
                chapterPicCount += 1;
                total += chapterPicCount;
            }
        }

        //优先遍历子节点
        auto child = item->FirstChild();
        if (child != nullptr && domElem != nullptr) { //当前节点是Element，并且孩子节点不为空，则深入下一层
            stack.push_back(domElem);
            item = child;
            continue;
        }

        //没有子节点，则遍历兄弟节点
        auto bro = item->NextSibling();
        if (bro != nullptr) {
            item = bro;
            continue;
        }

        //没有兄弟节点，则这一层已经遍历完，从stack中弹出上一层的没有遍历完的节点，得到该节点的兄弟节点
        bro = nullptr;
        while (true) {
            if (stack.empty()) {
                flag = false;
                break;
            }
            tinyxml2::XMLNode *lastNode = stack.back();
            stack.pop_back();

            if (lastNode == nullptr) {
                flag = false;
                break;
            }

            auto node = lastNode;

            bro = lastNode->NextSibling();
            if (bro != nullptr) {           // 该兄弟节点不为空，继续外层循环， 为空，则继续从栈顶拿结点
                break;
            }
        }

        //遍历上/上上级的兄弟节点
        if (flag && bro != nullptr) {
            item = bro;
        }
    }

    if (chapterWordCount > 0 && chapterIndex <= anchors.size()) {
        wordCounts.emplace_back(chapterWordCount, chapterPicCount);
    }
    chapterWordCount = 0;
    chapterPicCount = 0;

    return total;
}

bool xml_ext::empty_node(const tinyxml2::XMLElement *elem) {
    if (elem == nullptr) {
        return true;
    }
    std::string noSpineSrcName = "";
    bool nochild = elem->NoChildren();
    std::string name = xml_ext::ele_name(elem);
    bool noAttr = xml_ext::ele_params(elem, noSpineSrcName).empty();
    return nochild && noAttr && name != "br";
}

int xml_ext::parse(
        tinyxml2::XMLElement *element,
        std::vector<DocText> &docTexts,
        std::string &startAnchorId,
        std::string &endAnchorId,
        int *flagAdd,
        std::string &spineSrcName,
        int type) {

    std::string topParentTag;
    if (type == 0) {
        topParentTag = "body";
    } else if (type == 1) {
        topParentTag = "section";
    } else {
        LOGE("%s: faield, type[%d] is not support");
        return 0;
    }

    LOGD("%s: startAnchorId[%s] endAnchorId[%s], flagAdd[%d]", __func__, startAnchorId.c_str(), endAnchorId.c_str(), *flagAdd);

    if (element == nullptr) {
        LOGD("%s: failed, element is null", __func__);
        return 1;
    }
    tinyxml2::XMLNode *item = element;
    std::list<NodeTag> stack;
    bool flag = true;

    size_t offset = 0;                              //当前的偏移量
    std::stringstream ss;                           //接受的文本流
    std::vector<TagInfo> tags;                      //当前深度优先搜索对应的html标签集

    while (flag && item != nullptr) {
        std::string itemId = xml_ext::getEleAttr(item, "id");
        if (!endAnchorId.empty() && itemId == endAnchorId) {
            flag = false;
            *flagAdd = 2;
            break;
        } else if (!startAnchorId.empty() && itemId == startAnchorId) {
            *flagAdd = 1;
        }

        auto domElem = item->ToElement();
        if (*flagAdd == 1) {
            auto domText = item->ToText();
            if (domText != nullptr) {  //是文本节点, 文本节点的标签都在stack中
                const char *text = domText->Value();
                if (text != nullptr && strlen(text) > 0) {
                    std::string filtered_str = string_ext::cleanStr(text);
                    ss << filtered_str;
                    offset += string_ext::utf8Count(filtered_str);

                    //文本必然位于其上一层的html标签中，找到这个html标签
                    if (!stack.empty() && !tags.empty()) {
                        std::string puuid = stack.back().uuid;
                        while (!puuid.empty()) {             //循环更新每一个上级的结束位置索引
                            for (auto &ptag: tags) {
                                if (ptag.uuid == puuid) {
                                    ptag.endPos = offset;
                                    puuid = ptag.parent_uuid;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            if (domElem != nullptr) {                       //是Dom节点, 加入到标签
                if (!empty_node(domElem)) {
                    size_t endpos = 0;
                    if (domElem->NoChildren()) {
                        endpos = offset;
                    }
                    std::string parent_uuid;
                    if (!stack.empty()) {
                        parent_uuid = stack.back().uuid;
                    }
                    std::string tag_name = xml_ext::ele_name(domElem);
                    //开始一个新的节点之前，如果该节点是一个段落开始节点
                    if (is_paragraph_tag(tag_name)) {
                        if (tag_name == "table") { //如果是table标签，需要增加cols，rows，index等一些属性
                            handle_table_attr(domElem);
                        }

                        // 判断ss不为空, 或者tags中有不属于其父节点，
                        std::vector<TagInfo> otherTags = non_father_tags(parent_uuid, tags);
                        if (!otherTags.empty() || !ss.str().empty()) {
                            std::vector<TagInfo> docTags;
                            if (otherTags.empty()) {
                                std::vector<TagInfo> docTags0 = get_fathers_tags(parent_uuid, tags);
                                if (!docTags0.empty()) {
                                    docTags.insert(docTags.end(), docTags0.begin(), docTags0.end());
                                }
                            } else {
                                std::vector<TagInfo> docTags0 =get_fathers_tags(otherTags, tags);
                                if (!docTags0.empty()) {
                                    docTags.insert(docTags.end(), docTags0.begin(), docTags0.end());
                                }
                            }
                            //   则需要将其作为一个段落分出去
                            docTexts.emplace_back(DocText{ss.str(), docTags});
                            ss.str("");
                            ss.clear();
                            offset = 0;
                            endpos = 0;
                            //tags 中之需要保留父级结构
                            if (!otherTags.empty()) {
                                std::vector<TagInfo> parent = get_fathers_tags(parent_uuid, tags);
                                if (!parent.empty()) {
                                    for (auto &tag: parent) {
                                        tag.startPos = 0;
                                        tag.endPos = 0;
                                    }
                                }
                                tags.clear();
                                tags.insert(tags.end(), parent.begin(), parent.end());
                            }
                        }
                    }

                    if(tag_name != "br") {  //br 换行标签不需要插入记录了，已经分段了
                        auto tag = TagInfo{string_ext::generate_uuid(),
                                           xml_ext::getEleAttr(domElem, "id"),
                                           tag_name,
                                           offset,
                                           endpos,
                                           parent_uuid,
                                           xml_ext::ele_params(domElem, spineSrcName)};
                        tags.push_back(tag);
                    }
                }
            }
        }

        //优先遍历子节点
        auto child = item->FirstChild();
        if (child != nullptr && domElem != nullptr) { //当前节点是Element，并且孩子节点不为空，则深入下一层
            stack.push_back(NodeTag{domElem, tags.back().uuid});
            item = child;
            continue;
        }

        // 没有子节点，则当前标签结束， tag end // 设置tag的结束索引
        if (*flagAdd == 1) {
            //------------------------------------------------------------------ 一个节点没有子节点，则判断下需不要生成一个自然段落
            std::string name = xml_ext::ele_name(domElem);
            if (domElem != nullptr && !empty_node(domElem) && !name.empty() && name != "br") {
                //拿到html节点名称
                //body的直接子标签全部都是自然段落
                if (stack.empty()) {
                    std::string line = ss.str();
                    if (!tags.empty() || string_ext::utf8Count(line) > 0) {
                        docTexts.emplace_back(DocText{line, tags});
                    }
                    ss.str("");
                    ss.clear();
                    tags.clear();
                    offset = 0;
                    //一些段落标签/img/image，非body直接子标签，也需要处理成自然段落
                } else if (is_paragraph_tag(name)) {
                    if (!tags.empty()) {
                        //弹出最后一个加入的html标签
                        auto curTag = tags[tags.size() - 1];
                        tags.pop_back();
                        std::string line = ss.str();
                        //因为当前html标签内没有子元素，也就没有文本，则直接将其他html标签和文本内容作为一个段落
                        if (!tags.empty() || !line.empty()) {
                            docTexts.emplace_back(DocText{line, tags});
                        }
                        //清空
                        ss.str("");
                        ss.clear();
                        //tags 中的数量要清空到和stack中的数量一致，不能全部清空
                        while (tags.size() > stack.size()) {
                            tags.pop_back();
                        }
                        offset = 0;
                        //当前标签单独作为一个段落
                        curTag.startPos = 0;
                        curTag.endPos = 0;
                        std::vector<TagInfo> curTags;
                        //将tags中保留的也放入当前tags中
                        curTags.reserve(tags.size());
                        for (auto &tag: tags) {
                            curTags.push_back(tag);
                        }
                        curTags.push_back(curTag);
                        docTexts.emplace_back(DocText{"", curTags});
                    }
                }
            } else {
                //可能是文本节点，或者其他不需要作为分割自然段落的html节点， 这里不需要处理，当判断没有兄弟节点时候，会再次尝试判断需不需要分割自然段落
            }
            //------------------------------------------------------------------
        }

        //没有子节点，则遍历兄弟节点
        auto bro = item->NextSibling();
        if (bro != nullptr) {
            item = bro;
            continue;
        }

        //没有兄弟节点，则这一层已经遍历完，从stack中弹出上一层的没有遍历完的节点，得到该节点的兄弟节点
        bro = nullptr;
        std::vector<std::string> nodeTagUUIds;
        while (true) {
            if (stack.empty()) {
                break;
            }
            NodeTag lastNode = stack.back();
            stack.pop_back();

            if (lastNode.node == nullptr) {
                flag = false;
                break;
            }

            nodeTagUUIds.push_back(lastNode.uuid);
            auto node = lastNode.node;

            bro = lastNode.node->NextSibling();
            if (bro != nullptr) {           // 该兄弟节点不为空，继续外层循环， 为空，则继续从栈顶拿结点
                break;
            }
        }

        if (!nodeTagUUIds.empty() && *flagAdd == 1) {
            if (stack.empty()) { //栈空，则全部内容作为一个段落
                std::string line = ss.str();
                if (!tags.empty() || string_ext::utf8Count(line) > 0) {
                    docTexts.emplace_back(DocText{line, tags});
                }
                ss.str("");
                ss.clear();
                tags.clear();
                offset = 0;
            } else {
                bool newParagraph = false;
                for (auto &nodeTagUUID: nodeTagUUIds) {
                    std::string name;
                    for (auto &tag: tags) {
                        if (!nodeTagUUID.empty() && tag.uuid == nodeTagUUID) {
                            name = tag.name;
                            break;
                        }
                    }
                    if (is_paragraph_tag(name)) {
                        newParagraph = true;
                        break;
                    }
                }
                if (newParagraph) {
                    auto &nodeTagUUID = nodeTagUUIds.back();
                    TagInfo targetTag;
                    for (auto &tag: tags) {
                        if (!nodeTagUUID.empty() && tag.uuid == nodeTagUUID) {
                            targetTag = tag;
                            break;
                        }
                    }
                    if (targetTag.startPos != 0) { //需要将内容分成两个段落
                        size_t partPos = targetTag.startPos;
                        std::string line = ss.str();
                        if (partPos < line.size()) {
                            std::string partOne = line.substr(0, partPos);
                            std::vector<TagInfo> partOneTags;
                            std::string partTwo = line.substr(partPos);
                            std::vector<TagInfo> partTwoTags;
                            for (auto &tag: tags) {
                                if (tag.endPos < partPos) {
                                    partOneTags.push_back(tag);
                                } else if (tag.startPos > partPos) {
                                    partTwoTags.push_back(tag);
                                } else if (tag.startPos == 0 && tag.endPos > partPos) {
                                    partOneTags.push_back(tag);
                                    partTwoTags.push_back(tag);
                                }
                            }
                            docTexts.push_back(DocText{partOne, partOneTags});
                            docTexts.push_back(DocText{partTwo, partTwoTags});
                            ss.clear();
                            ss.str("");
                            offset = 0;
                            std::vector<TagInfo> parentTags;
                            for (auto &stack_item: stack) {
                                for (auto &tag: tags) {
                                    if (tag.uuid == stack_item.uuid && !stack_item.uuid.empty()) {
                                        parentTags.push_back(tag);
                                    }
                                }
                            }
                            tags.clear();
                            if (!parentTags.empty()) {
                                for (auto tag: parentTags) {
                                    tag.startPos = 0;
                                    tag.endPos = 0;
                                    tags.push_back(tag);
                                }
                            }
                        }
                    } else {    //不需要分段
                        std::string line = ss.str();
                        if (!tags.empty() || string_ext::utf8Count(line) > 0) {
                            docTexts.emplace_back(DocText{line, tags});
                        }
                        ss.str("");
                        ss.clear();
                        offset = 0;
                        std::vector<TagInfo> parentTags;
                        for (auto &stack_item: stack) {
                            for (auto &tag: tags) {
                                if (tag.uuid == stack_item.uuid && !stack_item.uuid.empty()) {
                                    parentTags.push_back(tag);
                                }
                            }
                        }
                        tags.clear();
                        if (!parentTags.empty()) {
                            for (auto tag: parentTags) {
                                tag.startPos = 0;
                                tag.endPos = 0;
                                tags.push_back(tag);
                            }
                        }
                    }
                }
            }
        }

        //遍历上/上上级的兄弟节点
        if (flag) {
            if (bro != nullptr) {
                item = bro;
            } else { //上层也没有bro了，则尝试获取上上层的兄弟节点
                tinyxml2::XMLNode *parent = item->Parent();
                if (parent == nullptr) {
                    flag = false;
                    break;
                }
                tinyxml2::XMLElement *ele_parent = parent->ToElement();
                if (ele_parent == nullptr) {
                    flag = false;
                    break;
                }
                std::string ele_name = xml_ext::ele_name(ele_parent);
                if (!ele_name.empty() || ele_name == topParentTag) {
                    flag = false;
                    break;
                }
                item = ele_parent->NextSibling();
            }
        }
    }

    LOGD("%s: invoke done", __func__ );
    return 1;
}

std::vector<std::pair<std::string, std::string>> xml_ext::parse_str_params(std::string &params) {
    std::vector<std::pair<std::string, std::string>> ret;
    if (!params.empty()) {
        std::vector<std::string> kvs = string_ext::split(params, '&');
        if (!kvs.empty()) {
            for (auto &kv: kvs) {
                std::vector<std::string> item = string_ext::split(kv, '=');
                if (item.size() == 2) {
                    std::string key = item[0];
                    std::string value = item[1];
                    string_ext::trim(key);
                    string_ext::trim(value);
                    if (key == "xlink:href" || key == "href" || key == "l:href") {
                        key = "src";
                    }
                    ret.emplace_back(key, value);
                }
            }
        }
    }
    return ret;
}

tinyxml2::XMLElement *xml_ext::getStartElement(tinyxml2::XMLElement *root, int *flagAdd, const std::string &anchorId) {
//    LOGI("%s:invoke", __func__);
    if (!root) {
        LOGE("%s failed, no root element", __func__);
        return nullptr;
    }
    auto body = root->FirstChildElement("body");
    if (!body) {
        LOGE("%s failed, no body element", __func__);
        return nullptr;
    }
    std::string bodyId = xml_ext::getEleAttr(body, "id");
    tinyxml2::XMLElement *childEle = body->FirstChildElement();

    if (anchorId.empty()) {
        *flagAdd = 1;
    } else {
        if (!bodyId.empty() && bodyId == anchorId) {
            *flagAdd = 1;
        } else {
            std::string firstId = xml_ext::getEleAttr(childEle, "id");
            if (!firstId.empty() && firstId == anchorId) {
                *flagAdd = 1;
            }
        }

        auto ele = xml_ext::findEleById(childEle, anchorId.c_str());
        if (ele != nullptr) {
            std::string eleName = xml_ext::ele_name(ele);
            if (eleName != "body") {
                childEle = ele;
                *flagAdd = 1;
            } else {
                ele = ele->FirstChildElement();
                if (ele != nullptr) {
                    childEle = ele;
                    *flagAdd = 1;
                }
            }
        }
    }
//    LOGI("%s:invoke done", __func__);
    return childEle;
}