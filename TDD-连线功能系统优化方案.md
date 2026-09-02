# TCHESS 连线功能系统优化方案

| 项目 | 内容 |
|---|---|
| 文档类型 | 技术设计文档（TDD） |
| 主题 | 连线功能系统优化方案 |
| 版本 | v1.0 |
| 日期 | 2026-09-02 |
| 状态 | 待评审 |
| 适用范围 | `com.sojourners.chess.linker` 及相邻模块（`controller`、`enginee`、`yolo`、`config`） |

---

## 1. 背景与目标

### 1.1 背景

连线功能是 TCHESS 中技术复杂度最高的模块，融合了 AI 视觉识别（YOLOv11/ONNX Runtime）、跨平台原生交互（JNA / xdotool / osascript）、棋盘状态对比算法、动画确认机制等技术。用户反馈存在"连线走棋卡住"问题，经深入源码分析定位到多处线程安全、状态管理与性能瓶颈。

### 1.2 优化目标

1. **消除卡死缺陷**：修复线程时序错乱与状态语义缺陷（`stopFlag` 新旧 bestmove 混淆）导致的连线走棋卡住问题
2. **提升可维护性**：降低 `Controller`、`AbstractGraphLinker` 等核心类的复杂度，消除 God Object 与魔法数字
3. **提升性能**：减少重复计算与无效推理，降低 CPU 占用
4. **增强可扩展性**：为后续接入识别/执行多策略（DOM、协议对接等）奠定架构基础

### 1.3 当前代码现状

| 模块 | 文件 | 规模 | 主要问题 |
|---|---|---|---|
| 主控制器 | `controller/Controller.java` | 1543 行 | 实现 3 个回调接口，God Object |
| 连线基类 | `linker/AbstractGraphLinker.java` | 622 行 | 核心算法复杂度高，魔法数字 |
| 引擎封装 | `enginee/Engine.java` | 414 行 | stopFlag 语义歧义（新旧 bestmove 混淆） |
| 视觉推理 | `yolo/Yolo5Model.java` | 388 行 | 每帧全量推理 |
| 配置管理 | `config/Properties.java` | 605 行 | Serializable 持久化，无版本迁移 |

---

## 2. 现状问题分析（根因）

### 2.1 缺陷 1：`Controller.bestMove()` 线程模型错位

位置：`controller/Controller.java:1156-1171`

    public void bestMove(String first, String second) {
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            ChessBoard.Step s = board.stepForBoard(first);

            Platform.runLater(() -> {          // JavaFX 线程异步执行
                board.move(...);
                goCallBack(first);             // → engineGo() → isThinking=true
            });

            if (linkMode.getValue()) {
                trickAutoClick(s);             // Engine 虚拟线程同步执行 → isThinking=false
            }
        }
    }

`trickAutoClick` 早于 `board.move` 执行，导致：

- `isThinking` 被提前复位（置为 false）
- `engineBoard`（本地棋盘）未及时更新，而目标平台已更新
- 扫描循环恢复后检测到 `linkBoard` 与 `engineBoard` 不一致，`compareBoard` 产生错误 Action 或持续返回 null，陷入死循环 —— 即"连线走棋卡住"根因。

### 2.2 缺陷 2：`isThinking` 状态标志非原子

位置：`controller/Controller.java:181`

    private volatile boolean isThinking;

跨越 JavaFX 线程、Engine 虚拟线程、Linker 虚拟线程三个线程读写。`volatile` 只保证可见性，不保证"检查—设置"复合操作的原子性。

### 2.3 缺陷 3：`Engine.stopFlag` 语义歧义（新旧 bestmove 无法区分）

位置：`enginee/Engine.java:200-204`、`enginee/Engine.java:310-358`

    private void bestMove(String msg) {
        if (stopFlag) {
            stopFlag = false;
            return;   // 丢弃引擎 bestmove
        }
        ...
    }

    public void analysis(...) {
        stop();               // stopFlag = true + cmd("stop")
        ...                   // 立即 cmd("position fen ...") + cmd("go")
    }

`stopFlag` 的真实语义是"丢弃下一个到达的 bestmove"，用于丢弃 `stop()` 后旧局面残留的 bestmove。但 `analysis()` 在 `stop()` 后**立即**发出新一轮 `go` 指令，引擎响应 `stop` 与计算新局存在异步延迟，代码**无法区分**到达的 bestmove 属于旧局面还是新局面。只要旧 bestmove 在新 `go` 之后才到达，新局面的 bestmove 也会被 `stopFlag==true` 误丢弃，导致 `isThinking` 永不复位、连线卡死。

**根因是 stopFlag 的语义歧义（无法关联 bestmove 所属的分析轮次），而非单纯的读写非原子性**——把它改为 `AtomicBoolean` 只保证单变量读写原子，不能区分"这一条回复属于哪一轮"。

### 2.4 问题 4：`compareBoard()` 复杂度失控

位置：`linker/AbstractGraphLinker.java:303-407`

105 行双重循环 + 8 个 if 分支，判断走棋方向（flag 1/2/3/4）的逻辑深埋其中，无测试锁定。

### 2.5 问题 5：魔法数字遍布

| 常量 | 位置 | 含义 |
|---|---|---|
| `0.8`（PADDING） | `yolo/OnnxModel.java:12` | 格宽/格高计算中的棋盘四周留白格数（`width/(8+PADDING*2)`） |
| `0.5`（CONFIDENCE） | `yolo/Yolo11Model.java:10` | 置信度阈值（存在字段遮蔽，见缺陷 7） |
| `640`（SIZE） | `yolo/OnnxModel.java:16` | 模型输入尺寸 |
| `0.45`（IoU） | `yolo/Yolo5Model.java:232` | NMS 阈值 |
| `9`（count） | `linker/AbstractGraphLinker.java:165` | 疑似新棋局阈值 |
| `0.2` | `linker/AbstractGraphLinker.java:590-598` | 边缘格偏移 |

### 2.6 问题 6：每帧全量 YOLO 推理

位置：`linker/AbstractGraphLinker.java:103`

每次扫描都对完整 640×640 输入做前向传播并识别全部 90 个格子，而象棋走棋本质只有 2 个格子变化，大量计算冗余。

### 2.7 缺陷 7：`CONFIDENCE` 字段遮蔽

位置：`yolo/OnnxModel.java:14` vs `yolo/Yolo11Model.java:10`

    // OnnxModel.java
    public final float CONFIDENCE = 0.75f;

    // Yolo11Model.java（遮蔽父类字段，包私有、非 final）
    float CONFIDENCE = 0.5f;

Java 字段解析为编译期静态绑定，导致同一次识别流程使用两个不同阈值：定位棋盘（`Yolo5Model.findBoardPosition(List)` 第 284 行）读到父类 `0.75`，识别棋子（`Yolo11Model.processOutput` 第 78 行）读到子类 `0.5`，行为不一致且隐蔽。治理时需删除子类遮蔽字段，统一为单一命名常量并补阈值一致性测试。

---

## 3. 短期改进（代码层面）

### 3.1 线程时序与状态语义修复（P0）

#### 修复步骤

1. **缺陷 1 修复（后台串行化，勿搬入 UI 线程）**：`trickAutoClick` 在前台模式下会执行真实 `robot.mouseMove/press/release` 且含多处 `robot.delay(...)`（`AbstractGraphLinker.java:437-462`），是**阻塞操作**。因此不能移入 `Platform.runLater`（JavaFX Application Thread），否则会把"走棋卡住"换成"界面冻结"。

   改为在后台串行化保证顺序：`goCallBack(first)` 成功回调后，通过单一执行队列/`CompletableFuture` 链串行执行 `board.move → goCallBack → trickAutoClick`。最终应与 4.2 `LinkCoordinator` 状态机统一设计，避免先打补丁后重构。

   预期效果：保证 `board.move` → `goCallBack`（`isThinking=true`）→ `trickAutoClick`（`isThinking=false`）严格有序，且不阻塞 UI 线程。

2. `isThinking` 由 `volatile boolean` 改为 `AtomicBoolean`，读写统一走 `get()/set()`。注意：当前代码中 `isThinking` 仅 3 个写入点 + 1 个读取点且无"检查-设置"复合操作，改为 `AtomicBoolean` 仅提升语义清晰度，**不能单独解决时序错乱**；真正根治依赖 4.2 的状态机。

3. `Engine.stopFlag` 改用**世代号（generation）**：每轮 `analysis()` 递增世代计数并随 `go` 捕获当前世代，`bestMove` 回调时比对世代，只处理当前世代的回复，旧世代回复自然丢弃。`stopFlag` 可保留为 `AtomicBoolean` 作为辅助，但最终正确性依赖世代号区分新旧 bestmove。

#### 对比分析

- **后台串行化**（方案采用） vs **搬入 `runLater`**：后者在前台 Robot 模式下会阻塞 UI 线程；串行化不改线程归属、仅保证顺序，无 UI 卡顿风险。
- **世代号方案** vs **AtomicBoolean only**：`AtomicBoolean` 只保证单变量原子读写，无法区分 bestmove 所属轮次；世代号直接消除语义歧义，改动小、无锁。
- **加锁/队列同步**：仅当需保护多字段一致性时引入；单标志位场景用无锁方案更优。

### 3.2 代码重构与可维护性治理（P1）

1. **拆分 `compareBoard()` 为三个纯函数**，各自可独立单测：
   - `diffBoards()`：产出差异点集
   - `classifyAction()`：分类 flag 1/2/3/4
   - `checkMoveLegality()`：走棋合法性校验（避免与 `Engine.validateMove` 重名）

   注意：`compareBoard` 中 flag 1 用 `canGo(engineBoard,...)`、flag 2 用 `canGo(linkBoard,...)`（`AbstractGraphLinker.java:387`），拆分时须保留这一"依赖不同棋盘"的语义，否则等价性测试会失效。

2. **魔法数字常量化**：在 `OnnxModel` 或独立常量类集中声明，语义化命名（如 `BOARD_PADDING`、`YOLO_CONFIDENCE_THRESHOLD`）。同时删除 `Yolo11Model` 遮蔽父类的 `CONFIDENCE` 字段（缺陷 7），消除 0.75/0.5 双阈值不一致，并补一条阈值一致性单测。

3. **平台空实现去重**：在 `AbstractGraphLinker` 提供 `screenshotByBack` / `mouseClickByBack` 默认空实现，子类覆盖支持的方法（模板方法模式），消除 `LinuxGraphLinker`、`MacosGraphLinker` 的重复空实现。

#### 预期收益

- `compareBoard` 的 flag 语义可被 JUnit 测试锁定，回归风险大幅下降
- 新平台接入成本从"改基类"降为"新增实现类"

### 3.3 性能调优（P1）

1. **缓存棋盘位置**：`findBoardPosition()` 初次定位后缓存，后续帧直接截图裁剪区域，不再全图搜索棋盘。
2. **差量识别**：YOLO 是对整张输入图一次前向，90 是输出 anchor 数量、无法"只算 2 格"。真正可降的是**后处理/精识别区域**——基于上一帧棋盘先轻量定位变化区，再对该区域裁剪精识别。收益体现在减少无效重识别与后处理，而非 ONNX 前向的网格计算量。
3. **自适应扫描间隔**：静止时拉长间隔，检测到变化时缩短间隔。

#### 对比分析

- **差量识别** vs **全量识别**：差量识别需重构 `findChessBoard` 支持局部区域输入，改动中等；收益来自裁剪区域减少重复后处理，而非前向推理量，量化目标应以 CPU/时延为准。
- **缓存位置** vs **每帧重定位**：缓存改动极小（一级 if 判断），即可消除每帧重复棋盘检测开销。

---

## 4. 中期改进（架构层面）

### 4.1 识别与执行策略解耦（策略模式）

#### 现状问题

`AbstractGraphLinker` 将"截图识别"（YOLO）与"走棋执行"（Robot/PostMessage）耦合在一个类，三平台实现通过继承硬编码，导致新识别方案（DOM 自动化、协议对接）与新走棋方式（键盘、Accessibility）无处安放。

#### 改进策略

将连线拆分为三个独立职责：

    IRecognizer（识别器）：截图 → 棋盘状态
      ├── YoloRecognizer（当前视觉方案）
      ├── DomRecognizer（浏览器 DOM）
      └── ProtocolRecognizer（协议对接）

    IMoveExecutor（执行器）：走棋 → 目标平台
      ├── MouseExecutor（Robot / PostMessage）
      ├── KeyboardExecutor
      └── AccessibilityExecutor

    GraphLinker（编排器）：识别 → 对比 → 决策 → 执行

| 维度 | 说明 |
|---|---|
| 适用场景 | 需接入多种目标平台（网页 + 桌面 + 协议）时 |
| 预期收益 | 识别与执行自由组合，新平台接入成本大幅下降 |
| 实施风险 | 中。需将继承重构为组合，回归测试工作量大 |

### 4.2 Controller 职责拆分（消除 God Object）

#### 现状问题

`Controller` 达 1543 行，同时实现 `EngineCallBack`、`LinkerCallBack`、`ChessManualCallBack` 三个接口，承担 UI 事件、引擎调度、连线逻辑、棋谱管理四类职责。

#### 改进策略

按职责抽取协调者（Mediator/Coordinator）：

    Controller（仅 UI + 事件分发）
      ├── EngineCoordinator（引擎调度、isThinking 状态机）
      ├── LinkCoordinator（连线状态机、识别/执行编排）
      └── ManualCoordinator（棋谱、趋势图）

| 维度 | 说明 |
|---|---|
| 适用场景 | 后续功能扩展前；1543 行已达维护瓶颈 |
| 预期收益 | 连线状态机可独立测试，围绕 `isThinking` 的竞态可被状态机消除 |
| 实施风险 | 高。牵涉全部三个回调接口迁移，需分阶段渐进抽取，建议先抽 `LinkCoordinator` |

### 4.3 配置持久化架构优化

#### 现状问题

`Properties` 用 Java 原生 `Serializable` 持久化整个对象（`config/Properties.java:188-205`），存在：

- 字段增删改导致反序列化失败，无版本迁移机制
- 序列化文件为二进制，无法手工排查修复
- 类结构跨版本演进需脆弱兼容逻辑

#### 改进策略

迁移到 JSON + Jackson 或 `java.util.prefs`，引入版本字段 + 迁移函数：

    { "version": 2, "linkScanTime": 100, "linkThreadNum": 2, ... }

| 维度 | 说明 |
|---|---|
| 适用场景 | 版本升级频繁、配置字段持续演进 |
| 预期收益 | 配置可读可修复，字段增减兼容性由版本号 + 迁移函数保证 |
| 实施风险 | 中。需处理存量 `properties` 文件读取与一次性迁移 |

### 4.4 走棋执行器可靠性增强（点击验证 + 降级）

#### 现状问题

后台 `PostMessage` 点击后无验证，若目标平台未响应（Electron / DirectX 程序），连线直接卡住，无重试或降级。

#### 改进策略

在执行器层引入"点击 → 截图验证 → 失败降级"闭环：

    点击走棋 → 等待 N 毫秒 → 截图验证棋盘是否变化
      ├── 已变化 → 继续
      ├── 截图无效（窗口被遮挡/最小化，无法识别）→ 提示用户
      └── 未变化 → 重试（限 1 次）→ 仍失败 → 降级前台 Robot → 提示用户

| 维度 | 说明 |
|---|---|
| 适用场景 | 后台模式不可靠的平台 |
| 预期收益 | 将"静默卡死"变为"可降级可恢复" |
| 实施风险 | 低。execute 层已有 `autoClick` 入口，验证逻辑复用现有 `findChessBoard`。注意后台 `PostMessage` 模式下窗口被遮挡/最小化时截图拿不到有效画面，需在降级判定中加入"截图无效"分支 |

---

## 5. 实施优先级与路线图

| 阶段 | 优先级 | 改进项 | 风险 | 前置依赖 |
|---|---|---|---|---|
| 短期 | P0 | 线程安全修复（3.1） | 低 | 无 |
| 短期 | P1 | 魔法数字治理 + 平台空实现去重（3.2） | 低 | 无 |
| 短期 | P1 | 棋盘位置缓存 + 差量识别（3.3） | 中 | 3.2 纯函数拆分 |
| 中期 | P2 | 走棋执行器降级（4.4） | 低 | 3.1 |
| 中期 | P2 | 识别/执行策略解耦（4.1） | 中 | 3.2 |
| 中期 | P3 | Controller 拆分（4.2） | 高 | 4.1 |
| 中期 | P3 | 配置持久化迁移（4.3） | 中 | 无 |

**推荐顺序**：先落地 P0 线程安全修复（以世代号方案解决缺陷 3、后台串行化解决缺陷 1，直接消除"卡住"且风险最低），随后推进纯函数拆分与 `CONFIDENCE` 字段遮蔽治理（为架构改造铺路），最后按 P2 → P3 逐步推进。每项改进应配对应单元测试，覆盖 `compareBoard` 等价性、`bestMove` 时序、世代号区分、阈值一致性等已定位缺陷的核心路径。

---

## 6. 风险分析

| 风险 | 等级 | 缓解措施 |
|---|---|---|
| 线程模型重构引入新竞态 | 中 | 分步小改，每步用单元测试锁定 |
| `stopFlag` 改世代号遗漏旧回复清理 | 中 | 世代计数单测 + 引擎联调验证 isThinking 不复位场景 |
| `compareBoard` 拆分改变既有行为 | 中 | 拆分前后做等价性测试 |
| `CONFIDENCE` 字段遮蔽修复改变识别阈值 | 中 | 阈值一致性测试 + 新旧阈值对比回归 |
| Controller 拆分影响三回调链路 | 高 | 渐进抽取，先抽 `LinkCoordinator` |
| 配置迁移导致用户配置丢失 | 中 | 双向兼容 + 一次性迁移 + 备份 |

---

## 7. 开放问题

1. 修复 `bestMove` 线程时序后，是否需要将 4.4"点击后验证"机制纳入 P0 一并落地，以彻底规避目标平台无响应？[TBD: 待与维护者确认]
2. 差量识别是否需保留全量校验兜底？[TBD: 待与维护者确认]
3. Controller 拆分的目标模块边界是否与当前开发计划一致？[TBD: 待与维护者确认]
4. 世代号方案中，`stopFlag`（辅助标志）与世代计数的语义边界及旧回复清理时机如何界定？[TBD: 待与维护者确认]