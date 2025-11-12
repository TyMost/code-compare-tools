# 文件系统持久化设计

## 背景与目标
- 在真正落地数据库方案前，为 `infrastructure.persistence` 模块提供一套临时可用的文件系统持久化实现。
- 满足现有仓储接口（`DiffRepository`、`MigrationRepository`、`RepoRepository`、`SettingRepository`、`CoverageRepository`）对保存、查询的需求。
- 运行环境限定为 JDK 8，优先使用 JDK 自带库，必要时借助当前项目中已经使用的 Jackson 生态。
- 保持接口不变，便于未来替换为数据库实现。

## 总体方案
### 存储根目录
- 通过 Spring 配置 `fileStorage.rootPath` 指定根目录，默认指向 `${user.home}/.migratediff/storage`。
- 初始化时调用 `Files.createDirectories(rootPath)` 保证目录存在。

### 目录结构
```
{root}/
  diff/                # DiffSummary 聚合
    {diffId}.json
  migration/           # MigrationTask 聚合
    {taskId}/task.json
    {taskId}/delta-group.json
  repo/                # RepoConfig 聚合
    index.json         # 维护所有 repoId 列表与元数据
    {repoId}.json
  setting/             # SystemSetting 聚合
    {key}.json
  coverage/            # CoverageSummary 聚合
    {taskId}.json
```

### ID 映射策略
- `DiffSummary`：使用 `baseCommitId + "_" + targetCommitId` 作为自然键，业务层需保证唯一性；若未来引入显式 `id` 字段，可替换为该字段。
- `MigrationTask`：直接使用 `task.getId()`。
- `CoverageSummary`：使用 `summary.getTaskId()`。
- `RepoConfig`：引入 `FileRepoKeyResolver`，以 `repoPath.getValue()` + `branchFrom` + `branchTo` 组合生成 key，特殊字符通过 URL Safe Base64 或哈希规避。
- `SystemSetting`：使用 `setting.getKey()`。

若缺失 ID（例如首个 `save` 时 `DiffSummary` 还没有 commit 信息），接口将返回自定义异常 `MissingIdentifierException`，提醒上层补齐。

## 数据序列化
- 使用 `com.fasterxml.jackson.databind.ObjectMapper`，集中在 `FileStorageObjectMapperFactory` 配置：
  - 注册 `JavaTimeModule` 处理 `Instant`、`LocalDateTime`。
  - 启用 `WRITE_DATES_AS_TIMESTAMPS = false` 保持可读性。
  - 对 Lombok 生成的 builder 兼容 Jackson 默认行为。
- 序列化时映射到细粒度 DTO，以消除 Lombok `@Builder.Default` 带来的空集合问题，必要时在 DTO 中做 null 安全。
- DTO 层统一放置在 `infrastructure.persistence.filesystem.dto` 包下，并由 MapStruct 或手写转换器完成与领域模型之间的转换。

## 写入流程
1. 调用 `KeyResolver` 获得目标文件路径。
2. 通过 `FileStorageSupport.ensureParentDirectory` 创建父目录。
3. 将领域对象转换为 DTO，序列化到临时文件 `{file}.tmp`。
4. 使用 `Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)` 原子替换；若底层文件系统不支持原子移动，则回退到普通 `REPLACE_EXISTING` 并记录警告日志。
5. 写入完成后返回原始领域对象。

针对 `RepoRepository.findAll()`：
- 保存时同步更新 `repo/index.json`（维护 repoId、仓库路径、最后更新时间）。
- 查询全部时先读 index 再批量构建对象，减少对文件系统的遍历次数。

## 读取流程
1. 根据 ID 定位文件路径，若不存在直接返回 `Optional.empty()`。
2. 读取 JSON，利用 ObjectMapper 反序列化到 DTO。
3. DTO 转换为领域对象后返回。
4. 对 `migration/{taskId}/delta-group.json` 等多文件方案，读取失败时需要同时记录日志并返回 `Optional.empty()`，避免返回不完整数据。

## 并发与一致性
- 每个仓储实现持有一个 `ReentrantReadWriteLock`；保存操作获取写锁，读取操作获取读锁，确保同一实体的读写安全。
- 对跨文件操作（如 `RepoRepository.save` 同步更新 index）在同一写锁中完成，避免状态不一致。
- 提供 `FileLockService`（可选）在多进程场景下利用 `FileChannel.tryLock()` 做粗粒度互斥。

## 错误处理与日志
- 定义 `FilePersistenceException extends RuntimeException`，包装 IO/序列化异常并附带上下文字段（entityType、key、operation）。
- 用 `Slf4j` 记录失败原因；对于可预期的异常（文件不存在）不打印堆栈，仅在调试级别输出。

## 配置与注入
- `@Configuration` 类 `FileSystemPersistenceConfig`：
  - 声明 `@Bean` `StorageProperties`，绑定 `fileStorage.*`。
  - 为每个仓储接口注册 `FileSystem*Repository` Bean。
  - 提供 `ObjectMapper` 和 `KeyResolver` Bean。
- 支持通过 `application.yml` 配置根路径、是否启用文件落地（便于未来切换 Database 实现时通过条件装配替换）。

## 测试策略
- 为每个仓储实现编写集成测试，使用 JUnit `@Rule TemporaryFolder` 或 JUnit 5 `@TempDir` 提供的临时目录。
- 覆盖以下场景：
  - 保存后能读取到相同对象（含时间字段、集合字段）。
  - 重复保存可覆盖旧值。
  - `RepoRepository.findAll()` 返回顺序可控（按 index.json 中的更新时间排序）。
  - 异常路径（缺失 ID、JSON 损坏）能正确抛出异常或返回 empty。

## 可演进性
- 文件系统实现完全遵循仓储接口，未来接入数据库仅需：
  - 新增 `Database*Repository`，实现同一接口。
  - 用 Spring Profile 或条件装配切换 Bean。
- DTO 转换逻辑保持在独立层，既可被文件系统版本复用，也可在数据库实现中沿用。

## 实现任务拆解
1. 新增配置类与 `StorageProperties`。
2. 编写 `FileStorageSupport`（目录、文件操作、临时文件写入）。
3. 为每个仓储接口实现对应的文件系统版本及 DTO。
4. 补充异常、锁服务、KeyResolver。
5. 编写并通过集成测试，验证核心读写路径。

