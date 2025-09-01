
cmake_minimum_required(VERSION 3.12)

project(unzip101e C)

set(CMAKE_C_STANDARD 99)

set(unzip101e_SOURCES
        crypt.h
        ioapi.c
        ioapi.h
        iowin32.h
        mztools.h
        unzip.c
        unzip.h
        zip.c
        zip.h
)

add_library(unzip101e ${unzip101e_SOURCES})


set_target_properties(unzip101e PROPERTIES
        OUTPUT_NAME "unzip101e")

target_link_libraries(unzip101e PUBLIC z)