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
    set(xiangqi_warning_options /W4 /WX /utf-8 /permissive-)
else()
    set(xiangqi_warning_options -Wall -Wextra -Wpedantic -Werror)
endif()
target_compile_options(xiangqi_core PRIVATE ${xiangqi_warning_options})

enable_testing()
add_executable(xiangqi_core_tests tests/core_tests.cpp)
target_link_libraries(xiangqi_core_tests PRIVATE xiangqi_core)
target_compile_options(xiangqi_core_tests PRIVATE ${xiangqi_warning_options})
add_test(NAME xiangqi_core_tests COMMAND xiangqi_core_tests)
