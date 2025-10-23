# Git 对比演示数据

此目录包含用于验证传统覆盖流程和新的增量 diff / 迁移流程的轻量级 Git 仓库。

## 仓库矩阵

| 代码 | 角色 | 基准引用 | 目标引用 | 亮点 |
| --- | --- | --- | --- | --- |
| `projectA-git` | 源仓库 | `refs/heads/main` | `refs/heads/feature/git-demo` | 原始覆盖率演示数据集。 |
| `projectB-git` | 目标仓库 | `refs/heads/main` | `refs/heads/release/git-demo` | `projectA-git` 的配套仓库，用于演练匹配和不匹配的覆盖块。 |
| `projectC-git/source-app` | 源仓库 | `refs/heads/main` | `refs/heads/feature/incremental-demo` | 展示新增、删除和修改案例的增量 diff 示例。 |
| `projectC-git/target-app` | 目标仓库 | `refs/heads/main` | `refs/heads/feature/incremental-demo` | 与 `source-app` 对应，便于流水线呈现并排的增量 diff。 |

> 提示：`projectC-git` 含有两个独立的 Git 仓库。验证增量模式时，将 `migration.scan.project.sources` 与 `migration.scan.project.targets` 指向相应的文件夹。

## 覆盖演示（projectA-git vs projectB-git）

- `src/main/java/com/example/demo/Calculator.java`：双方都进行了修改，会生成部分相似的覆盖片段。
- `src/main/java/com/example/demo/report/UsageReport.java`：双方都新增了该文件但内容不同，可用于演示低于 100% 匹配度的新增块。
- `docs/migration-plan.md`（仅源端）与 `docs/modernization-checklist.md`（仅目标端）：展示不匹配的新增内容。
- `docs/legacy-guidelines.md`：两个分支都删除了此文件，形成匹配的删除块。
- `src/main/java/com/example/demo/analytics/TrendAnalyzer.java`：目标端新增，整体覆盖率因此下降。

## 增量 Diff 速查表（projectC-git）

| 变更类型 | 源端（`feature/incremental-demo`） | 目标端（`feature/incremental-demo`） |
| --- | --- | --- |
| 修改 | `source-app/src/main/java/com/example/service/UserService.java` 增加空值保护和周年标签 | `target-app/src/main/java/com/example/order/OrderService.java` 新增四舍五入与忠诚度积分逻辑 |
| 新增 | `source-app/src/main/java/com/example/analytics/UsageTracker.java` | `target-app/src/main/java/com/example/billing/BillingReconciliation.java` |
| 删除 | `source-app/src/main/java/com/example/legacy/LegacyReportGenerator.java` | `target-app/src/main/java/com/example/maintenance/LegacyCleanupTask.java` |
| 配置差异 | `source-app/src/main/resources/application.properties` 调整超时与功能开关 | `target-app/src/main/resources/application.properties` 修改日志级别并启用对账功能 |

在每个仓库中运行 `git diff main feature/incremental-demo` 可以查看原始变更。随后启用 `migration.scan.diffEngine: git`，验证新的 diff 片段、并排渲染以及迁移动作（生成 / 应用 / 撤销）。

## 快速开始

1. 更新 `backend/src/main/resources/application.yml`，指向目标仓库及分支。
2. 运行扫描或 Git 对比导出，例如：
   ```powershell
   mvn -f backend/pom.xml -DskipTests spring-boot:run
   ```
   或者：
   ```powershell
   mvn -f backend/pom.xml -DskipTests exec:java -Dexec.args="--batch git-comparison"
   ```
3. 打开界面（`git-comparison` 仪表盘或迁移详情页），选择需要查看的项目对。

要重置数据集，请在仓库中检出 `main` 分支并删除对应的 feature 分支。
