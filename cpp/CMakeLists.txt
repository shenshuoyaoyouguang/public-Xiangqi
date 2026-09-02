cmake_minimum_required(VERSION 3.20)

project(xiangqi_core VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(xiangqi_core
    src/board.cpp
    src/manual.cpp
    src/openbook.cpp
    src/engine.cpp
)

target_include_directories(xiangqi_core PUBLIC include)

if (MSVC)
    target_compile_options(xiangqi_core PRIVATE /W4 /permissive-)
else()
    target_compile_options(xiangqi_core PRIVATE -Wall -Wextra -Wpedantic)
endif()

enable_testing()
add_executable(xiangqi_core_tests tests/core_tests.cpp)
target_link_libraries(xiangqi_core_tests PRIVATE xiangqi_core)
add_test(NAME xiangqi_core_tests COMMAND xiangqi_core_tests)
