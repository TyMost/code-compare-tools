# Repository 模块说明

## 模块职责
- 提供文件系统为主的持久化实现，保存 diff 快照、块决策、文件元数据与 Agent 建议信息。
- 为各模块提供统一的仓储接口，支持分页、历史版本、清理等操作。
- 根据配置自动创建目录、格式化 JSON，并预留切换其他后端的扩展点。

## 存储布局
- 根目录由 `migration.storage.location` 控制，默认 `build/storage`。
- 子目录映射关系由 `StorageProperties` 决定：  
  - `fileMetadataDir` → 文件指纹与摘要。  
  - `diffSnapshotDir` → 每次扫描生成的 diff 结果。  
  - `blockDecisionDir` → 块决策快照历史。  
  - `agentSuggestionDir` → Agent 建议缓存。  
  - `scanSummaryDir` → 全量/增量扫描摘要。
- `StorageFileHelper` 负责创建目录、拼接安全路径，防止路径穿越。

## 核心组件
- `backend/src/main/java/com/example/codecompare/rebuild/repository/config/RepositoryConfiguration.java`  
  - 基于 `StorageProperties` 与全局 `ObjectMapper` 注册各仓储实现。  
  - 允许未来通过条件注解切换到数据库实现。
- 文件仓储实现位于 `repository/filesystem`：  
  - `FileSystemDiffSnapshotRepository`、`FileSystemBlockDecisionRepository`、`FileSystemFileMetadataRepository`、`FileSystemAgentSuggestionRepository`。  
  - 使用 `JsonStore` 简化 JSON 读写，支持 Pretty Print。
- `backend/src/main/java/com/example/codecompare/rebuild/repository/support/StoragePurgeService.java`  
  - 提供按项目清理历史数据的能力，被扫描服务在 reload 时调用。

## 功能特性
- `PageRequest`/`PageResult` 提供统一的分页结构；历史查询默认按时间倒序返回。
- `FileSystemBlockDecisionRepository` 使用读写锁和缓存状态，避免频繁文件 I/O，并提供 `purgeOlderThan` 逐步清理旧快照。
- `DiffSnapshotRepository#findRecent` 支持分页获取指定比较 ID 的最新 diff，用于仪表盘。

## 配置与调优
- `migration.storage.backend` 目前支持 `FILE` 与 `MEMORY`，默认 `FILE`；若未来扩展内存实现，可通过配置切换。
- `migration.storage.pretty-print` 控制 JSON 格式化，便于人工审查或压缩磁盘。
- 建议在生产环境为存储目录配置定期备份，并监控磁盘使用量。

## 扩展建议
- 若要接入数据库，可以实现与接口对应的 JPA/MyBatis 版本，并在 `RepositoryConfiguration` 中基于 `StorageProperties.Backend` 选择实例。
- 为了支持多租户，可在仓储键路径上增加 `tenantId` 前缀，并扩展 `StorageProperties` 提供动态解析。
- 对于大规模历史数据，可在 `StoragePurgeService` 中引入异步清理或分片删除策略。

