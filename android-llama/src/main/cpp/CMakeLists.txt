cmake_minimum_required(VERSION 3.22.1)
project(sencha_llama)

set(LLAMA_CPP_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)

set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

add_subdirectory(${LLAMA_CPP_DIR} ${CMAKE_BINARY_DIR}/llama.cpp)

add_library(sencha_llama SHARED
    llama_jni.cpp
)

target_include_directories(sencha_llama PRIVATE
    ${LLAMA_CPP_DIR}/include
    ${LLAMA_CPP_DIR}/ggml/include
)

target_link_libraries(sencha_llama
    llama
    android
    log
)
