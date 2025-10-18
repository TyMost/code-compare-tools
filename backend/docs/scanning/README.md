# Scanning 模块说明

## 模块职责
- 提供全量与增量扫描能力，收集示例项目的文件差异与代码块数据。
- 将扫描结果统一存入仓储，并发布事件供 diff、统计等模块消费。
- 负责清理废弃文件、维护扫描摘要缓存。

## 主要组件
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/ScanConfiguration.java`：模块自动配置，装配扫描所需 Bean。
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/FileScanService.java`：扫描入口，封装全量/增量/重新加载逻辑。
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/FullProjectScanService.java`：遍历项目根目录，计算文件指纹差异。
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/GitChangeScanner.java` 与 `NoopGitChangeScanner`：预留 Git 增量对比实现。
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/ProjectDiffGenerator.java`：监听 `ScanCompletedEvent`，调用 `DiffService` 生成 diff 与块决策。
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/IgnoredArtifactCleaner.java`：在扫描后清理与配置不符的旧文件。
- DTO 与事件：`ProjectScanRequest`、`ScanSummary`、`ScanCompletedEvent` 描述请求与结果。

## 核心流程
1. 前端触发重新加载或全量扫描时，`FileScanService#reload/scanAll` 会根据 `ProjectRootRegistry` 解析根目录，准备 `ProjectScanRequest`。
2. `FullProjectScanService#scan` 遍历文件，结合 `FileFingerprintCalculator` 计算指纹，生成 `FileRecord` 列表与 `ScanSummary`。
3. 结果写入 `ScanResultRepository`（文件存储），并发布 `ScanCompletedEvent`。
4. `ProjectDiffGenerator#onApplicationEvent` 收到事件后：  
   - 若为全量扫描则清空旧数据；  
   - 遍历源/目标项目文件，调用 `DiffService` 生成 `BlockDiff`；  
   - 通过 `BlockDiffLabeler` 打标签，写入 `DiffSnapshotRepository`、`BlockDecisionRepository`、`AgentSuggestionRepository`。
5. 扫描完成后，`IgnoredArtifactCleaner` 清理无效文件，`MetricsAggregator` 会在后续请求中读取最新摘要。

## 配置项
- `backend/src/main/java/com/example/codecompare/rebuild/scanning/ScanProperties.java`  
  - `comparisonId`：默认比较 ID。  
  - `supportedExtensions`：支持的文件扩展名列表。  
  - `ignoreGlobs`：额外忽略模式。  
  - `autoCleanup`：是否启用自动清理。  
  - `maxFileSize`、`maxFileCount` 等用于限制扫描规模。
- 可以在 `application.yml` 中通过 `migration.scan.*` 调整。

## 事件与并发
- `ScanCompletedEvent` 携带 `summary`、`changedRecords`、`fullRescan` 标记，消费者需根据全量/增量选择不同处理逻辑。
- 扫描与 diff 生成默认在调用线程执行，如需异步可在外层使用线程池或消息队列包装。

## 扩展建议
- 接入真实 Git 增量时，需实现 `GitChangeScanner#scanIncremental`，并在配置中启用 `migration.git.enabled=true`。
- 若项目根目录较多，可在 `ProjectScanRequest` 中扩展分片策略，避免一次遍历耗时过长。
- 可在 `ProjectDiffGenerator` 中添加指标收集（如耗时、失败文件），并通过 `MetricsAggregator` 暴露给前端。

