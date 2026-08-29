# 贡献指南（CONTRIBUTING）

感谢关注 TCHESS！欢迎通过 Issue 反馈问题、通过 Pull Request 提交代码。

## 开发环境

- JDK 21（项目使用 Java 21 虚拟线程与最新语法）
- Maven 3.9+（`mvn verify` 为最低验证标准，含编译、测试与覆盖率门禁）
- JavaFX 23、ONNX Runtime 等依赖均声明在 `pom.xml`，**不要引入新的第三方库**（测试框架 JUnit 除外）

## 常用命令

```bash
mvn compile        # 编译
mvn test           # 运行单元测试（src/test，JUnit 5）
mvn verify         # 编译 + 测试 + JaCoCo 覆盖率门禁 + 打包
mvn javafx:run     # 运行桌面程序
```

## 提交规范

提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/)：

```
<type>(<scope>): <subject>
```

- type：`feat` / `fix` / `docs` / `style` / `refactor` / `perf` / `test` / `build` / `ci` / `chore` / `revert` / `release`
- 示例：`feat(ui): 局势图支持关键节点标注`、`fix(engine): 修复 UCCI 协议 setoption 参数顺序`
- CI 会校验新增提交的格式，不符合规范的提交会导致流水线失败

## 代码约定

详见 [AGENTS.md](AGENTS.md)，重点：

- 项目为 JPMS 模块（`open module Xiangqi`），新增依赖需同步 module-info
- 引擎读取与连线循环使用 Java 21 虚拟线程，不要换成平台线程
- UI 样式集中在 `resources/style`（dark/light 两套主题都要检查）
- 中文注释与日志是现有风格
- **行为变更必须带测试**：`src/test` 下有 56 个用例（走子规则/将军判定/FEN/配置序列化/中文棋谱翻译），新功能请在对应测试类补充用例；修复 bug 建议先写复现用例
- 不要新增 `printStackTrace` / `System.out`，统一使用 `System.getLogger`

## Pull Request 流程

1. 从 master 切出功能分支
2. 完成改动并通过 `mvn verify`
3. 提交 PR（CI 会跑三平台矩阵构建），PR 描述说明改动动机与验证方式
4. 平台相关改动（尤其 `linker` 包）需说明 Windows/Linux/macOS 三端的影响
5. 重构类改动请保持每个 PR 原子化、可独立回滚

## 反馈问题

提交 Issue 前请先搜索已有 Issue；报告连线或识别问题时，请附上 `samples/` 目录下的识别失败样本与日志文件（`logs/tchess*.log`）。
