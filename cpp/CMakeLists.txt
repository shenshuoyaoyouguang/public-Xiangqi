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
    # check_cxx_source_compiles 只编译不执行——GBK 运行时可用性不在此验证，
    # 解码路径运行时兜底（CP936/ISO-8859-1），PgnManual::gbk_supported() 可探测。
    # 缓存变量名不能用旧版 check_include_files 写过的 XIANGQI_HAVE_ICONV：
    # 结果已在缓存时本检查会被跳过，旧构建目录会拿到过期结论导致 core 链接失败。
    include(CheckCXXSourceCompiles)
    find_library(XIANGQI_ICONV_LIBRARY NAMES iconv libiconv)
    if (XIANGQI_ICONV_LIBRARY)
        set(CMAKE_REQUIRED_LIBRARIES ${XIANGQI_ICONV_LIBRARY})
    endif()
    check_cxx_source_compiles([[
#include <iconv.h>
int main() {
    iconv_t converter = iconv_open("UTF-8", "GBK");
    iconv_close(converter);
    return 0;
}
]] XIANGQI_ICONV_COMPILES)
    unset(CMAKE_REQUIRED_LIBRARIES)
    if (XIANGQI_ICONV_COMPILES)
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
