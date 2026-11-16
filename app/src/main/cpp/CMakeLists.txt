cmake_minimum_required(VERSION 3.22.1)
project("mcaddonai")

add_subdirectory(llama.cpp)

add_library(mcaddonai SHARED LlamaBridge.cpp)

target_link_libraries(mcaddonai
    llama
    android
    log
)
