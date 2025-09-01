# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)
# 设置编译选项（启用C++17和多线程）
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -pthread")

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("appmobi")

add_subdirectory(unzip101e)
add_subdirectory(libmobi)
add_subdirectory(cssparser)
add_subdirectory(threadlibs)
add_subdirectory(tidy-html5)
add_subdirectory(utfcpp)
add_subdirectory(base64)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        appmobi.cpp
        util/log.h

        util/mobi/common.c
        util/mobi/common.h
        util/mobi/mobimeta.c
        util/mobi/mobimeta.h
        util/mobi/mobidrm.c
        util/mobi/mobidrm.h
        util/mobi/mobitool.c
        util/mobi/mobitool.h
        util/mobi/save_epub.h
        util/mobi/save_epub.cpp

        util/book_util.h
        util/chapter_count.h
        util/css_ext.h
        util/css_ext.cpp
        util/meta_data.h
        util/tidyh5_ext.h
        util/tidyh5_ext.cpp
        util/zip_ext.h
        util/zip_ext.cpp
        util/xml_ext.h
        util/xml_ext.cpp
        util/nav_point.h
        util/tag_info.h
        util/css_info.h
        util/doc_text.h
        util/bitmap_ext.cpp
        util/bitmap_ext.h
        util/app_ext.h
        util/app_ext.cpp
        util/file_ext.h
        util/file_ext.cpp
        util/mobi_util.h
        util/mobi_util.cpp
        util/epub_util.h
        util/epub_util.cpp
        util/fb2_util.h
        util/fb2_util.cpp

        util/tinyxml2.h
        util/tinyxml2.cpp
        util/string_ext.h
        util/string_ext.cpp
        util/file_searcher.h
        util/file_searcher.cpp
        util/crc_util.h
        util/crc_util.cpp
)

# tinyxml2 的debug开关
#add_definitions(-DTINYXML2_DEBUG)
add_definitions(-DANDROID_NDK)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/tidy-html5/include)
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/utfcpp/source)

target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,common-page-size=16384")

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        PRIVATE utf8cpp
        PRIVATE tidy-static  # 正确的链接静态库
        unzip101e
        cssparser
        threadlibs
        base64
        mobi
        # List libraries link to the target library
        z

        jnigraphics
        android
        log)