# AGENTS.md — public-Xiangqi (TCHESS)

跨平台中国象棋 GUI（JDK 21 + JavaFX 23，Maven，GPLv3）：UCI/UCCI 引擎对弈与分析、棋谱（PGN/XQF/TXQ）、开局库（本地多格式 + 云库）、屏幕连线（YOLO 识别第三方平台棋盘并自动代走）。三块代码：`src/main`（Java GUI）、`src/test`（JUnit 5）、`cpp/`（独立 C++20 核心，**CI 不覆盖**）。用户文档：README / MANUAL / CHANGELOG / CONTRIBUTING（中文）。

## 常用命令与硬约束

```bash
mvn verify                       # CI 标准：编译+测试+JaCoCo 分支门禁（XiangqiUtils/JsonPropertiesCodec BRANCH ≥ 0.40）
mvn test -Dtest=ChessBoardTest   # 单跑一个测试类
mvn javafx:run                   # 运行桌面程序（com.sojourners.chess.Main）
cmake -S cpp -B cpp/build && cmake --build cpp/build && ctest --test-dir cpp/build   # 改 cpp/ 必须本地跑
```

- 除 JUnit（test scope）外**不引入新第三方库**，不引入日志/JSON 框架（用 `System.Logger` + 自研 `JsonPropertiesCodec`）。
- 新增依赖必须同时改 `pom.xml` 与 `module-info.java`（JPMS）。包级循环 `board`↔`config` 已存在，**不要新增跨包循环**。
- JaCoCo 的 `destFile`/`dataFile` 指向 `${java.io.tmpdir}` 而非 `target/`（Windows 中文路径写不进 target），别"顺手改回来"。
- `enginee`（双 e）是历史包名，别重命名。surefire `useModulePath=false`，测试跑 classpath，反射可用。

## 盘面坐标与记法（最容易写错）

- 盘面 `char[10][9]`，`board[行][列]`，行 0 = 黑方底线、行 9 = 红方底线，大写 = 红，空格是 `' '`（不是 `'\0'`/`'.'`）。
- 引擎/ICCS 着法 4 字符串：列 `'a'+x`、行 `9-y`（`ChessBoard.stepForEngine`/`stepForBoard`）。
- FEN 只有 `XiangqiUtils.fenToBoard`（解析）与 `ChessBoard.fenCode`（生成），**没有 boardToFen**。
- 中文记法解析极宽松（`translate`/`translateCnMove`），改这里必须补 `ChessNotationTest`；`cnMove` 含空格，任何地方不能 `split(" ")`。
- 镜像换算 `x→8-x, y→9-y` 手写重复**四处**（`BaseBoardRender`、`AbstractGraphLinker.compareBoard`、`EngineController.autoClickTactic`、`Controller.canvasClick`），改一处要同查另三处。

## 引擎层（`enginee`）

- 协议是字符串 `"uci"`/`"ucci"`（无枚举），差异只有握手行与 setoption 语法（`setoption name K value V` vs `setoption K V`，且分叉写在两处）。无 `isready`/`ucinewgame`，无棋钟概念。
- 非标准用法：`searchmoves` 拼在 `go <时限>` **之后**；ponder 着法拼在 `position` 行尾而非 `go ponder` 后。动命令拼装必须同步。
- 子进程工作目录必须是引擎 exe 的父目录；行分隔符 `line.separator`，每条命令 write 后立即 flush。
- 并发防护**只有 volatile 标志位**，stdout 恰一条虚拟线程读。不要"顺手加锁"、不要换平台线程。
- `stopFlag`：`stop()` 置位、由下一个 bestmove 吞掉并复位；`moveNow()` 只发裸 `stop` 不置位，语义相反别合并。
- `Threads`/`Hash` 延迟到下一次 `analysis()` 才下发。转发 bestmove 前在读取线程 sleep 随机延迟；`nextInt(start,end)` 等值会抛异常终止读循环（现存隐患，别复制）。

## UI 装配（`controller`）

- `Controller` 是装配根，`initialize()` 顺序有依赖：先建子控制器 → `session.bindButtons(...)` → `initChessBoard()` → 最后 `loadEngine()`。
- 对局模式状态只有 `GameSession` 的 6 个属性，必须走其方法（`toggleMode`/`switchPlayer`/`newChessBoard` 等）修改，别在别处 `setValue` 绕过。
- **连线进行中改任何设置必须调 `session.checkLinkMode()`**（语义 = 断开重连）。
- 场景图/控件 items 更新必须 `Platform.runLater`（已知反例 `showBookResults`，别扩散）；分析刷新有 150ms 节流 + 128 条上限。
- 对话框固定套路：`App.createStage` + `APPLICATION_MODAL` + `showAndWait`，回传靠目标 Controller 的 `public static` 字段。别引入新框架。

## 连线栈（`linker`/`yolo`/`jna`/`mouse`）

- 新增平台只实现 `AbstractGraphLinker` 的 4 个抽象点（窗口 ID/位置/后台抓屏/后台点击）。
- 扫描线程是虚拟线程，`stop()` 只做 `interrupt()`，退出全靠 interrupt 检查——别去掉、别换平台线程。
- 后台抓屏/点击仅 Windows 有实现（非 Windows 黑屏是现状）。跨平台隔离靠"类不被引用就不初始化"：**非平台类里引用 `WindowsGraphLinker`/`User32Extra`/`jna.platform.win32` 会把 `Native.load("user32")` 拖进 Linux/macOS 启动路径导致崩溃**。
- YOLO 防错常量都是行为参数，改一个就改灵敏度：nms IoU 0.45、`for k < 15` 是 labels 长度硬编码副本、`Yolo11Model.CONFIDENCE` 是字段遮蔽基类。别"整理"。

## 数据层（`manual`/`openbook`）

- 格式分派靠 `ManualController` 静态 `manualServices` Map + FileChooser `ExtensionFilter`，新增格式两处同改。可写只有 `.txq .pgn`（XQF save 是空方法）。
- TXQ = `ChessManual`/`ManualRecord` 的 Java 原生序列化：改包名/类名/字段名/类型会让所有旧 `.txq` 读不回来。
- 开局库键算法不要动：`.obk`/`.pfBook` 用 Zobrist 固定表（改表值或坐标方向 = 现存库全废），`.xqb` 用自家 XQKEY。多库结果只拼接不重排。
- XQF 魔数（Dong Shiwei seed、1024B 偏移、GBK）就是格式规范，别抽成常量类。PGN：编码探测 BOM→UTF-8→GBK；`Format=Chinese` 只翻译主线。

## 配置（`config`）

- `Properties.getInstance()` 懒加载 `properties.json`；`prop.save()` 只在 `Controller.exit()` 与 `ColorSettingController` 调用，新界面要即时落盘得自己调。
- 新增普通字段**不需要**改 codec（反射 `FIELDS`）；例外：`EngineConfig` 字段是手写 JSON，必须改 `JsonPropertiesCodec`。不支持类型（Map/嵌套对象/`List<对象>`）会让整份配置保存失败。
- `Properties` 的 25 参构造器按位置传值，插参/换序会静默错位——新字段用字段初始化器，别扩构造器。新字段必须补 `JsonPropertiesCodecTest` 回环用例。
- 版本号单一真源是 `pom.xml`（注入 `build.properties`），`App.VERSION` 不是硬编码常量，别"恢复"成字面量。

## 外部资源

- `PathUtils.getJarPath()` 返回值**带尾 `/`**。`model/`、`sound/`、`ui/` 按文件系统路径读取（不在 classpath），新增要同步 `release.yml` 的 cp 步骤。

## 测试约定

- JUnit 5 + 中文 `@DisplayName` + `@Nested` 分组；包私有类；无 mock 库。
- 不起 JavaFX：`ChessBoard` 构造传 `null` Canvas（渲染有 headless 空保护）。
- 不起真实引擎进程：照 `EnginePonderTest`——`Unsafe.allocateInstance` 跳过构造器 + 反射注入 `cb`/`writer`/标志位驱动状态机。

## C++ 核心（`cpp/`）

- 改 Java 核心规则（走法/将军/FEN/记法/引擎协议）时检查 `cpp/src`+`cpp/include` 同名镜像实现，**两边同步**（只有 Java 侧有 CI）。
- 有意的语义差异：C++ 中文着法严格校验（失败 `nullopt`）；C++ 引擎层有 mutex 串行化，不照搬 Java 的 sleep 妥协。

## 提交与日志

- Conventional Commits（CI commit-lint 校验，scope 用包名）；修 bug/加功能同步 `CHANGELOG.md` 的 `[Unreleased]`；用户可见变化更新 `MANUAL.md`。
- 日志每类一行 `System.getLogger(...)`；`Main.main` 第一件事 `Logging.init()`。**禁止 `System.out`/`printStackTrace`**。
- 保留"为什么这么写"的防错/协议妥协注释——它们就是回归测试说明书；注释里 `#NN` 是 GitHub issue。
