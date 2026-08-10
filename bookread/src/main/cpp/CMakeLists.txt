# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.22.1)

project("sheenbidi_jni")

# ── SheenBidi（Unicode Bidirectional Algorithm, Apache-2.0） ──────────────────
# 用 Unity build 模式：Source/SheenBidi.c 在 SB_CONFIG_UNITY 宏下 include 所有 .c，
# 一次编译产出全部算法代码。依赖只有 4 个标准 C 头（stdint/stdlib/string/stddef）。
# 纯 C，无 C++，无外部依赖，4 架构通用。
add_library(sheenbidi STATIC
        SheenBidi/Source/SheenBidi.c)
target_include_directories(sheenbidi PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}/SheenBidi/Headers
        ${CMAKE_CURRENT_SOURCE_DIR}/SheenBidi/Source)
target_compile_definitions(sheenbidi PUBLIC SB_CONFIG_UNITY)
# 关闭实验性 Text API（默认关闭，显式声明避免被环境变量打开）
target_compile_definitions(sheenbidi PRIVATE
        SB_CONFIG_SCRATCH_BUFFER_SIZE=8192
        SB_CONFIG_SCRATCH_POOL_SIZE=3)

# ── JNI 桥：Kotlin ↔ SheenBidi ────────────────────────────────────────────────
# UTF-16 零拷贝：Java String → GetCharArrayElements → SBCodepointSequence(UTF-16)
add_library(${CMAKE_PROJECT_NAME} SHARED
        sheenbidi_jni.cpp)
target_link_libraries(${CMAKE_PROJECT_NAME}
        PRIVATE sheenbidi
        PRIVATE log)

# Android 15 (API 35) 16KB page size 对齐要求
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,max-page-size=16384")
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE "-Wl,-z,common-page-size=16384")
