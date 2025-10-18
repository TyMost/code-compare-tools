# 后端文档总览

本目录按照重构版 Code Compare 后端的模块功能拆分子文件夹，帮助团队成员快速定位代码、配置与运行流程。

## 文档目标
- 提供面向模块的自解释文档骨架，降低二次开发的上手成本。
- 描述跨模块的协作关系，明确核心数据与事件的流向。
- 梳理关键配置项及其所在文件，方便排查环境差异。
- 汇总扩展注意事项与常见演进路径，指导后续重构。

## 模块索引
| 模块 | 文档目录 | 主要职责 |
| --- | --- | --- |
| 核心基础 (Core) | [core/README.md](core/README.md) | 公用配置、线程池、应用属性绑定、项目根目录管理。 |
| API 接口层 (API) | [api/README.md](api/README.md) | REST 控制器、视图装配、统一响应与异常处理。 |
| 差异数据模型 (Block) | [block/README.md](block/README.md) | 行级差异模型与代码快照对象，供 diff、仓储与统计共用。 |
| Diff 引擎 (Diff) | [diff/README.md](diff/README.md) | 行级 diff 计算、内容脱敏、结果装配与配置。 |
| 规则系统 (Rules) | [rules/README.md](rules/README.md) | 规则装载、缓存与策略执行，用于标签过滤与内容处理。 |
| 仓储层 (Repository) | [repository/README.md](repository/README.md) | 文件型持久化、分页查询、清理策略与工厂配置。 |
| 扫描流程 (Scanning) | [scanning/README.md](scanning/README.md) | 全量/增量扫描、示例项目 diff 生成、事件发布。 |
| 指标统计 (Stats) | [stats/README.md](stats/README.md) | 指标聚合、代码块分页、仪表盘视图映射。 |
| 迁移 Agent (Agent) | [agent/README.md](agent/README.md) | 注解模板装配、代码块迁移两步流程、本地 Agent 编排。 |

## 架构总览
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/FileScanService.java` 触发全量或增量扫描，发布 `ScanCompletedEvent`。
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/ProjectDiffGenerator.java` 监听事件，调用 `DiffService` 生成 `BlockDiff` 与仓储快照。
- `backend/src/main/java/com/example/codecompare/rebuild/repository/config/RepositoryConfiguration.java` 将结果落地到 JSON 文件存储，并对外提供仓储接口。
- `backend/src/main/java/com/example/codecompare/rebuild/stats/MetricsAggregator.java` 汇总仓储数据，供 `BlockStatsService` 与 `DashboardSummaryService` 提供查询。
- `backend/src/main/java/com/example/codecompare/rebuild/api/controller` 下的控制器暴露 REST API，并通过 `MigrationViewMapper` 装配前端视图。
- `backend/src/main/java/com/example/codecompare/rebuild/agent/CodeBlockMigrationService.java` 在用户操作时回写注解、刷新 diff、同步仓储。

## 配置入口
- 应用属性集中于 `backend/src/main/resources/application.yml`，核心属性绑定在 `backend/src/main/java/com/example/codecompare/rebuild/core/properties/ApplicationProperties.java`。
- 规则默认从 `classpath:config/rules.yaml` 读取，可通过 `migration.rules.path` 指向外部文件。
- 仓储目录与模式由 `migration.storage.*` 配置控制，详见 `backend/src/main/java/com/example/codecompare/rebuild/repository/config/StorageProperties.java`。
- Agent 注解模板路径通过 `migration.agent.annotation-template` 设置，默认读取本地 YAML 模板。

## 建议阅读顺序
1. 先通读 [core/README.md](core/README.md) 了解公共配置与路径约束。
2. 根据需求选择 [scanning/README.md](scanning/README.md) 与 [diff/README.md](diff/README.md) 了解数据生成链路。
3. 阅读 [repository/README.md](repository/README.md) 与 [stats/README.md](stats/README.md) 掌握持久化与统计流程。
4. 最后参考 [api/README.md](api/README.md) 与 [agent/README.md](agent/README.md) 了解对外接口与交互动作。

## 维护约定
- 文档与代码保持一一对应，新增模块或重要类时需同步说明。
- 所有路径均使用仓库相对路径，便于在 IDE 中直接跳转。
- 当引入外部依赖或调整配置格式时，请在对应模块文档的“扩展建议”小节补充注意事项。

