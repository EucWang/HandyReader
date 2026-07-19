cmake_minimum_required (VERSION 3.8)

project (base64)

add_library(base64
        cdecode.h
        cencode.h
        decode.h
        encode.h
        cencode.c
        cdecode.c
)
