# Agent 模块说明

## 模块职责
- 实现代码块迁移的“两步式”流程：先生成带注解的副本，再应用到目标项目，并支持回滚。
- 统一 Agent 建议的缓存、远程拉取与占位策略，支撑前端在无真实 Agent 时的演示体验。
- 管理注解模板、Diff 同步与快照写入，确保仓储与仪表盘与迁移操作保持一致。

## 关键组件
- `backend/src/main/java/com/example/codecompare/rebuild/agent/CodeBlockMigrationService.java`：核心服务，封装生成、应用、撤销注解副本的逻辑，并通过 `BlockDecisionRepository`、`BlockStatsService`、`AnnotatedFileWriter`、`DiffSynchronizationService` 完成数据回写。
- `backend/src/main/java/com/example/codecompare/rebuild/agent/LocalAgentOrchestrator.java`：本地编排器，整合缓存 (`AgentSuggestionCache`)、远程网关 (`AgentSuggestionGateway`) 与占位 Agent (`MigrationAgent`)，同时代理迁移操作入口。
- `backend/src/main/java/com/example/codecompare/rebuild/agent/migration/snapshot/BlockDecisionMutationService.java`：负责读取、更新 `BlockDecisionSnapshot`，并生成带注解的 `BlockDecisionRecord`。
- `backend/src/main/java/com/example/codecompare/rebuild/agent/migration/io/AnnotatedFileWriter.java` 与 `ProjectFileResolver`：负责将渲染后的注解插入到真实文件，并处理路径、编码、备份等细节。
- `backend/src/main/java/com/example/codecompare/rebuild/agent/migration/diff/DiffSynchronizationService.java`：迁移完成后刷新 diff 快照，确保前端可见的数据即时一致。
- `backend/src/main/java/com/example/codecompare/rebuild/agent/AnnotationTemplateProvider.java`：加载 YAML 注解模板，支持配置变更刷新。

## 核心流程
1. **获取 Agent 建议**  
   `LocalAgentOrchestrator#getSuggestion` 按顺序尝试缓存、远程网关、本地占位实现，并通过 `CodeBlockMigrationService#resolveAnnotationMetadata` 将已有注解模板回填到元数据。
2. **生成注解副本**  
   - `CodeBlockMigrationService#generateAnnotatedCopies` 根据块 ID 读取 `CodeBlockDetailDTO`，通过 `AnnotationRenderingService` 渲染模板。  
   - `AnnotatedFileWriter` 写出临时文件，`BlockDecisionMutationService` 更新块状态为“已注解”，并持久化快照。
3. **应用注解副本**  
   - `CodeBlockMigrationService#applyAnnotatedCopies` 将副本写回目标仓库文件，更新块状态为“已迁移”。  
   - `DiffSynchronizationService` 触发增量 diff，同步 `DiffSnapshotRepository` 与仪表盘统计。
4. **撤销注解副本**  
   - 回退块状态到原始版本，清理注解模板和本地文件缓存。

## 数据与并发控制
- `CodeBlockMigrationService` 使用 `ConcurrentHashMap<String, ReentrantLock>` 按文件粒度加锁，避免多线程同时写入同一目标文件。
- 迁移操作对 `BlockDecisionRepository`、`DiffSnapshotRepository` 的写入都在 try/catch 中封装，保障单块失败不会影响批量流程。
- `MigrationGroupingService` 会将同一文件的多个块按行号合并处理，减少 I/O 次数。

## 配置与资源
- `backend/src/main/java/com/example/codecompare/rebuild/agent/config/MigrationAnnotationProperties.java` 绑定 `migration.agent.annotation-template`，可切换不同模板文件。
- 模板文件默认位于 `backend/src/main/resources/migration/annotation-template.yml`，可通过 `AnnotationTemplateProvider#refresh` 热加载。
- 操作日志统一用 `LoggerFactory.getLogger` 输出，便于结合 `StructuredLoggerFactory` 进行结构化采集。

## 扩展建议
- 若要接入真实 Agent，可在 `AgentSuggestionGateway#fetchRemoteSuggestion` 中实现 HTTP 调用，并扩展 `AgentSuggestion` 元数据字段。
- 当需要支持多模板策略，可在 `AnnotationTemplateProvider` 内按项目或块标签切换模板，并在 `MigrationViewMapper` 中返回可选模板列表。
- 可在 `CodeBlockMigrationService` 添加钩子接口（如监听器或事件）以接入审批、审计流程。

