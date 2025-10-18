# Block 模块说明

## 模块职责
- 定义行级差异与代码快照的领域模型，为 diff、仓储、统计和 Agent 等模块提供统一的数据结构。
- 提供与 Jackson 兼容的序列化配置，确保 `BlockDiff` 可以稳态保存到 JSON 或通过 REST 下发。

## 核心数据结构
- `backend/src/main/java/com/example/codecompare/rebuild/block/model/BlockDiff.java`  
  - 描述一次差异片段包含的起始行、变更行数、替换次数、相似度等指标。  
  - `labelIds`、`labelDescriptors` 支持多标签，`DiffMetrics` 内嵌额外统计。  
  - Builder 默认做输入合法化（起始行最小为 1、空集合转不可变列表），并将内容统一为 `\n` 换行。
- `backend/src/main/java/com/example/codecompare/rebuild/block/model/CodeSnapshot.java`  
  - 封装单侧代码版本，包括语言、路径、内容。  
  - `of`/`builder` 提供不可变建造器，默认语言为 `plain`，空内容处理为空字符串。

## 典型使用场景
- `DiffService` 返回的差异结果由 `DiffResultAssembler` 组装为 `BlockDiff` 列表，随后写入 `DiffSnapshotRepository`。
- `MetricsAggregator` 读取 `BlockDiff` 的标签、行数，计算迁移完成率和分类占比。
- `CodeBlockMigrationService` 根据 `BlockDiff` 行号在目标文件中插入注解，并依据 `labelDescriptors` 更新块状态。

## 与其他模块的边界
- Block 模块不关注持久化与业务规则，仅提供纯数据结构。  
- 标签命名与含义由 `rules` 模块决定，Block 模块只保存结果。  
- 若需要新增字段，应同步更新 `DiffResultAssembler`、仓储模型与前端 DTO。

## 扩展建议
- 新增指标时，请优先考虑是否放入 `DiffMetrics`，保持 `BlockDiff` 的主体字段稳定；若需扩展，可通过 `Builder` 提供向后兼容构造。
- 若引入多语言支持，可在 `CodeSnapshot` 中添加语法高亮标记或文件编码字段，注意更新序列化和默认值。
- 为避免大对象序列化压力，建议保留 `sourceLines`、`targetLines` 的惰性使用模式，不要在无需求的场景下填充整段源码。

