# AGENTS.md — public-Xiangqi (TCHESS)

跨平台中国象棋 GUI（JDK 21 + JavaFX 23，Maven，GPLv3）。支持加载 UCI/UCCI 协议引擎进行对弈、分析，以及棋谱解析、开局库、屏幕连线（识别第三方平台棋盘并自动走子）。面向用户的文档是中文的 [README.md](README.md) 和 [MANUAL.md](MANUAL.md)。

## 常用命令

```bash
mvn compile            # 编译（验证改动的最低标准）
mvn javafx:run         # 运行桌面程序
```

无单元测试目录，验证靠 `mvn compile` + 手动运行。所有依赖都在 pom.xml，不要引入新的第三方库。

## 模块系统（JPMS）

`src/main/java/module-info.java` 定义 `open module Xiangqi`，只 exports `com.sojourners.chess` 包。新增第三方依赖必须同时在 module-info 加 `requires`，否则编译失败。

## 架构与包职责

- `board` — 棋盘核心：`ChessBoard`（局面/走子逻辑）、`BaseBoardRender`（渲染）
- `controller` — JavaFX 控制器，与 `src/main/resources/fxml/*.fxml` 一一对应；`handle` 子包存放事件处理
- `enginee` — 引擎进程管理。`Engine` 通过 Process 的 stdin/stdout 收发文本命令，双协议差异需注意：uci 为 `setoption name X value Y`，ucci 为 `setoption X Y`
- `linker` — 连线功能：`AbstractGraphLinker` 为平台无关骨架，`WindowsGraphLinker`/`LinuxGraphLinker`/`MacosGraphLinker` 为平台实现；Linux 依赖 `xdotool`
- `yolo` — ONNX Runtime 棋盘图像识别（Yolo5/Yolo11），模型文件在 `resources/model`
- `jna` — Win32 API 调用（仅 Windows 可用）；`mouse` — jnativehook 全局鼠标钩子
- `manual` — 棋谱解析（PGN/XQF/TXQ 实现）
- `openbook` — 开局库（多格式文件 + 云端 `CloudOpenBook`）
- `config` — `Properties` 单例 + `JsonPropertiesCodec`（手写 JSON 序列化，无依赖），配置存于 jar 同目录 `properties.json`
- `model` — 纯数据类

## 关键约定与坑

- **`config.Properties` 遮蔽了 `java.util.Properties`**，文件内 import 时注意包名。
- **新增 `Properties` 字段必须同步更新 `JsonPropertiesCodec` 的 toJson/fromJson**，否则配置丢失。
- 平台相关改动需兼顾 Windows/Linux/macOS 三端（尤其 `linker` 包），不能只按 Windows 行为写死。
- 线程模型：引擎读取和连线循环使用 Java 21 虚拟线程（`Thread.ofVirtual()`），不要随意换成平台线程。
- UI 样式集中在 `resources/style`（含 dark/light 主题 CSS），改界面时两套主题都要检查。
- 中文注释与日志是现有风格，跟随即可。
