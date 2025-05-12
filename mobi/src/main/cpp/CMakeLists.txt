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
add_subdirectory(threadlibs)

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

        util/mobi_util.h
        util/mobi_util.cpp

        util/string_ext.h
        util/string_ext.cpp
        util/file_searcher.h
        util/file_searcher.cpp
        util/crc_util.h
        util/crc_util.cpp
)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        unzip101e
        threadlibs
        mobi
        # List libraries link to the target library
        z

        android
        log)