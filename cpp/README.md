# Xiangqi C++ core

这是从 Java 核心逻辑迁移出的独立 C++20 工程，当前不依赖 JavaFX、Qt 或第三方 C++ 库。

已迁移并可测试：

- `board`：初始局面、FEN 读写与方向翻转、棋子走法、将军/将杀、局面校验、ICCS/中文着法转换
- `model`：棋谱节点、棋谱容器、开局库结果、引擎配置和思考数据
- `manual`：PGN（Chinese/ICCS）读写、主线和注释；编码探测顺序 UTF-8 BOM → UTF-16 BOM → UTF-8 → GBK（Windows 内置；POSIX 需系统 iconv，可用 `PgnManual::gbk_supported()` 探测）→ ISO-8859-1 兜底（任意字节可读，未知编码可能乱码）
- `openbook`：`OpenBook` 查询/排序接口和无依赖 `FunctionOpenBook` 适配器
- `enginee`：UCI/UCCI 命令构造、`info`/`bestmove` 解析、ponder 状态机和 Windows/POSIX 进程管道

## 构建

有 Visual Studio、Ninja 或 Make 生成器时：

```bash
cmake -S cpp -B cpp/build
cmake --build cpp/build
ctest --test-dir cpp/build --output-on-failure
```

当前 Windows 开发环境没有 CMake 可用的构建程序，因此也可以直接用 GCC 验证：

```powershell
New-Item -ItemType Directory -Force cpp/build | Out-Null
$sources = @(Get-ChildItem cpp/src -Filter '*.cpp' | ForEach-Object FullName) + @(Get-ChildItem cpp/tests -Filter '*.cpp' | ForEach-Object FullName)
g++ -std=c++20 -Wall -Wextra -Wpedantic -Werror -I cpp/include $sources -o cpp/build/xiangqi_core_tests.exe
./cpp/build/xiangqi_core_tests.exe
```

## 尚未直接兼容的外部格式

- TXQ 是 Java `ObjectOutputStream` 私有序列化格式，C++ 没有标准库兼容实现；当前不读取/写入 TXQ。
- XQF 涉及 GBK、版本密钥和二进制偏移，当前未接入；需要以样本文件建立 golden tests 后迁移。
- XQB、OBK、pfBook 和云开局库依赖 SQLite/HTTP 后端；核心只提供后端接口，避免把具体第三方库耦合进规则核心。

这些限制是有意标出的迁移边界，不影响 `cpp/tests/core_tests.cpp` 对已实现核心行为的验证。
