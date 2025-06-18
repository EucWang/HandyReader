//
// Created by MAC on 2025/5/29.
//

#include "app_ext.h"

std::string app_ext::appFileDir = "";

std::string app_ext::appCacheDir = "";

/***
 * @return 获取系统API版本
 */
int app_ext::getVersion() {
    char sdk[128] = "0";
    __system_property_get("ro.build.version", sdk);
    int sdk_version = atoi(sdk);
    return sdk_version;
}

