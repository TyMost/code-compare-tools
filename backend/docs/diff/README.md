# Diff 模块说明

## 模块职责
- 负责对比源/目标代码片段，生成行级差异 (`BlockDiff`) 及统计信息。
- 为扫描、仓储、Agent 等模块提供统一的 diff 服务接口，并支持内容脱敏与规则过滤。

## 处理流程
1. 上游通过 `DiffService#analyze` 提交 `DiffRequest`（包含 `CodeSnapshot`）。  
2. `DiffService` 根据 `DiffConfigurationProperties` 处理：  
   - `shouldIgnore`：使用 `IgnorePatternMatcher` 判断是否跳过指定路径。  
   - `validateSize`：校验文件大小不超过 `maxFileSize`。  
   - `maskContent`：调用 `DiffContentMasker`（默认 `RuleBasedDiffContentMasker`）进行脱敏。  
3. `DiffEngine`（默认 `LineDiffEngine`）使用 `github-java-diff-utils` 生成 `Patch`。  
4. `DiffResultAssembler` 将 `Patch` 转换为 `BlockDiff` 列表，补充行号、标签、`DiffMetrics` 等信息。
5. 调用方（如 `ProjectDiffGenerator`）将结果写入 `DiffSnapshotRepository`。

## 主要组件
- `backend/src/main/java/com/example/codecompare/rebuild/diff/DiffService.java`：流程入口，封装忽略、脱敏、组装逻辑。
- `backend/src/main/java/com/example/codecompare/rebuild/diff/LineDiffEngine.java`：默认 diff 引擎，基于 `DiffUtils.diff`。
- `backend/src/main/java/com/example/codecompare/rebuild/diff/DiffResultAssembler.java`：负责将 `Patch` 转换为 `BlockDiff`，并按规则打标签。
- `backend/src/main/java/com/example/codecompare/rebuild/diff/DiffContentMasker.java`：定义脱敏接口；`RuleBasedDiffContentMasker` 通过规则系统加载 `content-mask` 类型规则并编译为正则。
- `backend/src/main/java/com/example/codecompare/rebuild/diff/config/DiffConfigurationProperties.java`：绑定 `diff.*` 配置，目前支持 `max-file-size`、`context-lines`、`ignore-patterns`。

## 与规则模块的协作
- `RuleBasedDiffContentMasker` 使用 `RuleLoader` 读取 `rules.yaml` 中 `content-mask` 类型配置，可通过 `applyTo` 字段控制作用范围（SOURCE/TARGET/BOTH）。
- 当 `migration.rules.reload-on-change=true` 时，脱敏规则会在每次调用前尝试刷新，保证热更新。

## 错误处理与日志
- 大文件或不支持的编码会触发 `DiffEngineException`，调用方需捕获并反馈给用户。
- 脱敏规则编译失败时仅记录警告并跳过该规则，避免阻塞主流程。
- 对于空 diff，`DiffService` 会直接返回 `DiffResult.empty()`，上游需兼容。

## 扩展建议
- 若需引入语义 diff，可新增 `DiffEngine` 实现并在配置中切换 Bean。
- 对于复杂脱敏场景，可扩展 `RuleDefinition`，如增加脚本或多段替换，注意在规则模块同步更新解析逻辑。
- 建议为新增 diff 策略编写单元测试，覆盖忽略模式、脱敏规则与大文件边界。

