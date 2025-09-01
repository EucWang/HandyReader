//
// Created by MAC on 2025/5/29.
//

#ifndef U_READER2_APP_EXT_H
#define U_READER2_APP_EXT_H

#include <sys/system_properties.h>
#include <string>

class app_ext {

public:

    static std::string appFileDir;

    static std::string appCacheDir;

    /***
     * @return 获取系统API版本
     */
    static int getVersion();

};


#endif //U_READER2_APP_EXT_H
