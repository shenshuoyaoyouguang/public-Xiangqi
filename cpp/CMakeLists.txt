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

if (NOT WIN32)
    # POSIX 下 GBK 解码依赖系统 iconv；显式探测并链接（glibc/musl/macOS 通常已在 libc）。
    # 探测必须编译+链接一起过：只查头文件会在 iconv.h 存在但库不可链时让 core 链接失败。
    # 探测失败时 core 仍可用，只是 PGN 读不了 GBK 文件（PgnManual::gbk_supported() 可探测）。
    include(CheckCXXSourceCompiles)
    find_library(XIANGQI_ICONV_LIBRARY NAMES iconv libiconv)
    if (XIANGQI_ICONV_LIBRARY)
        set(CMAKE_REQUIRED_LIBRARIES ${XIANGQI_ICONV_LIBRARY})
    endif()
    check_cxx_source_compiles([[
#include <iconv.h>
int main() {
    iconv_t converter = iconv_open("UTF-8", "GBK");
    return converter == (iconv_t) -1 ? 1 : 0;
}
]] XIANGQI_HAVE_ICONV)
    unset(CMAKE_REQUIRED_LIBRARIES)
    if (XIANGQI_HAVE_ICONV)
        target_compile_definitions(xiangqi_core PRIVATE XIANGQI_HAS_ICONV=1)
        if (XIANGQI_ICONV_LIBRARY)
            target_link_libraries(xiangqi_core PRIVATE ${XIANGQI_ICONV_LIBRARY})
        endif()
    endif()
endif()

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
