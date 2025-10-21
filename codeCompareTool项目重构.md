# Code Compare Tool 项目重构说明

## 项目定位
- 面向多端仓库迁移与差异治理的内部平台，支持全量/增量扫描、块级比对、标签统计与人工审核。
- 覆盖“扫描→差异→块→统计→人工处理”完整链路，便于企业在内网环境中落地代码搬迁与治理。
- 预留 Agent 协同能力，但在当前内网场景下以本地两步式迁移为主，确保离线可运行。

## 架构概览
- **后端**：Spring Boot 2.7.x + Java 8（JDK 1.8），模块划分为 `core`、`scanning`、`diff`、`block`、`stats`、`repository`、`agent`、`api` 等。
- **前端**：Vue 2.7 + Element UI 2.15，配合 Vue CLI 5、Vuex、Vue Router，结合 diff2html、monaco-editor、echarts 完成仪表盘、代码块列表、差异详情三大界面。
- **示例仓库**：`examples/projectA` 与 `examples/projectB` 演示典型迁移流程。
- **通信协议**：REST API（以 `/api/v1/migration/**` 为主），前后端统一 `success/message/data` 响应格式。

## 关键功能组件
- **文件扫描 (`FullProjectScanService`)**：支持全量遍历 + 并行指纹计算，生成 `FileRecord` 与 `ScanSummary`；Git 增量接口已预留（当前返回空结果）。
- **差异分析 (`DiffService`)**：拼装块比对请求，调用块模块输出 `BlockDiff` 列表，统一封装指标。
- **块级迁移 (`block` 模块)**：负责代码抽取、顺序匹配与标签装配，将结果映射为前端可消费模型。
- **统计与看板 (`DashboardSummaryService` / `BlockStatsService`)**：聚合扫描与块决策数据，支持仪表盘、列表分页与详情页。
- **Agent 两步式迁移（当前策略）**
  1. **同步拷贝阶段**：将源库代码 1:1 迁移到目标库，并在代码外围添加注解标识（示例：`/** 这是一段迁移的代码 ... 这是一段迁移的代码结束 */`），确保迁移范围可追溯。
  2. **本地智能加工阶段**：本地 AI Agent 根据配置的规则文件直接修改目标代码，当前接口仅返回占位提示“等待后续建设”，不依赖外部网络。
  - 远程 AI API 占位符已保留，待未来内网能力开放后可快速接入。
- **配置中心 (`application.yml`)**：统一管理项目根路径、规则文件、存储目录等核心参数，可按需扩展 Git、Agent 等配置项。

## 前端要点
- **仪表盘**：展示项目路径、最新扫描时间、类别占比、新代码占比等；刷新按钮触发后端全量扫描。
- **代码块列表**：支持按标签、路径筛选与分页；批量操作按钮与 Agent 入口当前返回“等待后续建设”提示。
- **差异详情**：对比原/新代码片段，展示标签、行号与当前状态；保留 AI 建议面板占位信息。
- **状态管理**：Vuex 管理概览、列表、详情与 AI 建议的状态，统一请求 `src/api/migration.js`。

## 后端 API 对照
1. **概览**：`GET /api/v1/migration/overview` —— 支持 `projectKey`、`refresh` 参数（refresh 会触发一次全量扫描）。
2. **代码块列表**：`GET /api/v1/migration/code-blocks` —— 支持标签、路径、分页筛选。
3. **代码块详情**：`GET /api/v1/migration/code-blocks/{id}` —— 返回原/新代码、行号、标签等。
4. **Agent 占位接口**：`GET /api/v1/migration/code-blocks/{id}/ai-suggestion`、`POST /api/v1/migration/agent/execute` —— 当前固定返回“等待后续建设”，供前端展示占位信息。
5. **批量操作占位**：`POST /api/v1/migration/code-blocks/generate|apply|ignore` —— 后端仅记录请求并返回提示，方便后续接入真实业务。

## 部署与运行
- **后端**：JDK 8（推荐 1.8u321+），推荐使用 Maven 3.8+；运行 `mvn spring-boot:run` 或 IDE 内启动 `RebuildCodeCompareApplication`。
- **前端**：Node.js 16+（推荐 18），`npm install && npm run serve`；默认代理后端 `http://localhost:8081`。
- **配置**：`migration.project-roots` 指定需要扫描的目录；`migration.storage.location` 控制持久化路径；`migration.rules.path` 指定本地 Agent 使用的规则文件。
- **示例流程**：
  1. 设置项目根、规则、存储路径。
  2. `GET /api/v1/migration/overview?refresh=true` 触发初次扫描。
  3. 前端仪表盘查看统计 → 列表筛选差异块 → 详情页检查代码。
  4. 手动执行两步式迁移脚本或本地 Agent，等待后续功能整合。

## 扩展与重构建议
- **规则体系**：引入 DSL、多维条件满足本地 Agent 改造与标签判定的复杂场景。
- **差异引擎**：补充语义/结构化对比，提升复杂文件场景的精度。
- **Agent 协同**：当内网开放 AI 服务后，替换占位实现，完善建议审核、回滚与权限控制。
- **存储演进**：接入数据库、对象存储记录扫描版本、Agent 改造历史与操作日志。
- **性能优化**：完善增量扫描策略、目录缓存、批量写入与监控告警。
- **前端体验**：增加批量操作反馈、标签颜色体系、本地 Agent 状态面板与历史回溯能力。

## 已知限制与注意事项
- 当前 Git 增量扫描未实现，所有刷新均触发全量扫描，建议结合定时任务或脚本控制频次。
- Agent 接口仅为占位，真实代码改造需依赖本地脚本或人工处理；前端会提示“等待后续建设”。
- 缺少统一的异常/日志包装与自动化测试，需要在后续迭代中补齐。
- 对于超大仓库，建议先在本地执行两步式迁移并分批导入，避免一次性扫描耗时过长。
