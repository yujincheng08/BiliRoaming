cmake_minimum_required(VERSION 3.28)
project(biliroaming)

set(CMAKE_CXX_SCAN_FOR_MODULES ON)

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

add_subdirectory(dex_builder)

add_library(${PROJECT_NAME} SHARED
        biliroaming.cc
        )

target_link_libraries(${PROJECT_NAME} PUBLIC log dex_builder_static)

if (NOT DEFINED DEBUG_SYMBOLS_PATH)
    set(DEBUG_SYMBOLS_PATH ${CMAKE_BINARY_DIR}/symbols)
endif()

add_custom_command(TARGET ${PROJECT_NAME} POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E make_directory ${DEBUG_SYMBOLS_PATH}/${ANDROID_ABI}
        COMMAND ${CMAKE_OBJCOPY} --only-keep-debug $<TARGET_FILE:${PROJECT_NAME}>
        ${DEBUG_SYMBOLS_PATH}/${ANDROID_ABI}/${PROJECT_NAME}
        COMMAND ${CMAKE_STRIP} --strip-all $<TARGET_FILE:${PROJECT_NAME}>)
