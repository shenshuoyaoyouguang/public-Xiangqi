# AGENTS.md

## 项目概述

TCHESS — 跨平台中国象棋界面程序，支持 UCI/UCCI 协议引擎对弈、分析、连线、开局库、棋谱等功能。版本 1.9。

## 技术栈

- **语言**: Java 21（模块化项目，模块名 `Xiangqi`）
- **UI 框架**: JavaFX 23（FXML + CSS 主题）
- **构建工具**: Maven
- **AI 识别**: ONNX Runtime + YOLOv11 模型（用于连线时棋盘识别）
- **原生交互**: JNA（窗口操作）、jnativehook（全局鼠标监听）
- **数据库**: SQLite JDBC（本地开局库）

## 构建与运行

```bash
# 编译
mvn compile

# 运行（需要 JDK 21 + JavaFX 23）
mvn javafx:run

# 构建 jlink 镜像（生成独立可执行包）
mvn javafx:jlink
```

主类入口: `com.sojourners.chess.Main` → `App`（JavaFX Application）

## 架构要点

### 包结构 (`com.sojourners.chess.*`)

| 包 | 职责 |
|---|---|
| `board/` | 棋盘渲染。`ChessBoard` 是核心，`BoardRender` 接口有 `DefaultBoardRender`（程序绘制）和 `CustomBoardRender`（图片主题）两种实现 |
| `controller/` | JavaFX 控制器。`Controller` 是主控制器（~1500行），实现 `EngineCallBack`、`LinkerCallBack`、`ChessManualCallBack` 三个回调接口 |
| `enginee/` | 引擎封装。`Engine` 类通过 `Process` 与外部 UCI/UCCI 引擎通信（stdin/stdout） |
| `linker/` | 连线功能。`AbstractGraphLinker` 为基类，`Windows/Linux/MacosGraphLinker` 为平台实现。使用 YOLO 模型识别屏幕棋盘 |
| `yolo/` | ONNX 模型封装。`Yolo11Model` 继承 `Yolo5Model`，用于连线时的棋盘图像识别 |
| `openbook/` | 开局库。`OpenBookManager`（单例）管理云库 + 本地库（.xqb/.obk/.pfBook 三种格式） |
| `manual/` | 棋谱格式。`ChessManualService` 接口有 PGN/XQF/CBR/TXQ 四种实现 |
| `config/` | `Properties`（单例，Serializable）管理全部应用配置 |
| `jna/` | Windows User32 JNA 接口定义 |
| `mouse/` | 全局鼠标监听（基于 jnativehook） |
| `lock/` | `SingleLock`（wait/notify 互斥锁）、`WorkerTask` |

### 关键设计

- **棋盘数据**: `char[10][9]` 二维数组，10行9列
- **配置管理**: `Properties` 是单例 + Serializable，持久化到本地文件
- **主题系统**: light/dark 两套 CSS，`App.applyTheme()` 递归应用到所有窗口和子控件
- **资源路径**: `PathUtils.getJarPath()` 获取 JAR 所在目录，用于加载运行时资源（音效、开局库等）
- **版本号**: 硬编码在 `App.java`（`VERSION = "1.9"`, `BUILT_ON = "20260801"`）

### 资源目录 (`src/main/resources/`)

- `fxml/` — 9 个 FXML 文件（主界面 + 各对话框）
- `model/yolov11.onnx` — YOLO 棋盘识别模型
- `ui/` — 自定义主题的棋盘/棋子图片（用户可替换）
- `style/` — CSS 样式（light-theme/dark-theme + 控件样式）
- `sound/` — 走棋音效

## 开发注意事项

1. **模块化项目**: `module-info.java` 中 `requires` 了所有依赖模块，新增依赖需同步更新
2. **平台相关代码**: `linker/` 下有三个平台实现，修改连线逻辑需注意跨平台兼容性
3. **引擎通信**: `Engine` 类通过 `Process` 启动外部引擎进程，使用 `BufferedReader/Writer` 通信，注意线程安全和 `volatile` 标志位
4. **YOLO 模型**: 连线识别依赖 `yolov11.onnx`，模型文件在 `resources/model/` 下，修改识别逻辑需了解 ONNX Runtime API
5. **FXML 对话框**: 所有对话框通过 `App.java` 的静态方法创建和管理（`openEngineDialog`、`openTimeSetting` 等），保持一致的创建模式
6. **主题切换**: 修改 CSS 时注意 light/dark 两套主题需同步更新，`App.refreshTheme()` 会刷新所有窗口
7. **开局库格式**: 本地库支持 `.xqb`、`.obk`、`.pfBook` 三种格式，分别对应 `XqbOpenBook`、`BhOpenBook`、`PfOpenBook`
8. **棋谱格式**: 支持 PGN/XQF/CBR/TXQ 四种格式，通过 `ChessManualService` 接口统一抽象