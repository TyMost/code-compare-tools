# Stats 模块说明

## 模块职责
- 聚合仓储中的 diff 与块决策数据，计算分类占比、行数统计等指标。
- 为 API 层提供代码块分页、详情查询与仪表盘概览服务。
- 维护扫描摘要缓存，减少重复 I/O 与聚合成本。

## 核心组件
- `backend/src/main/java/com/example/codecompare/rebuild/stats/MetricsAggregator.java`  
  - 订阅 `ScanCompletedEvent`，更新内存缓存。  
  - 提供 `latestSummary`、`categoryMetrics`、`loadCodeBlocks`、`findBlockDetail` 等查询接口。  
  - 通过分页遍历 `DiffSnapshotRepository`/`BlockDecisionRepository` 汇总标签与行数。
- `backend/src/main/java/com/example/codecompare/rebuild/stats/BlockStatsService.java`  
  - 面向 API 封装分页与详情查询，依赖 `StatsViewMapper` 将结果转换为 DTO。  
  - 在详情缺失时尝试跨项目查找，提升容错。
- `backend/src/main/java/com/example/codecompare/rebuild/stats/DashboardSummaryService.java`  
  - 组合 `MetricsAggregator` 与 `ProjectRootRegistry`，返回仪表盘概览。
- `backend/src/main/java/com/example/codecompare/rebuild/stats/StatsViewMapper.java` 与 DTO 包  
  - 负责将领域模型转换为视图对象，如 `DashboardOverviewDTO`、`CodeBlockPageDTO`、`CodeBlockDetailDTO`。

## 指标聚合流程
1. `MetricsAggregator#categoryMetrics` 分页读取 diff 快照，累积标签数量、行数、代码块总数。  
2. 若快照缺失行数，会根据 `BlockLabelConstants.STATUS_MIGRATED` 补齐。  
3. `loadCodeBlocks` 组合 `DiffSnapshotDocument` 与 `BlockDecisionRecord`，按文件、类别过滤并排序。  
4. 结果经 `StatsViewMapper` 转换后由 API 返回前端。

## 缓存策略
- 最近一次扫描摘要 (`ScanSummary`) 缓存在 `ConcurrentMap` 中，按 `projectCode` 区分，减少频繁磁盘读取。
- 代码块分页查询属于实时计算，如需进一步优化，可考虑在仓储层引入增量索引或缓存。

## 与其他模块的交互
- 依赖仓储模块提供的 `DiffSnapshotRepository`、`BlockDecisionRepository`、`ScanResultRepository`。
- 与 `scanning` 模块通过事件衔接，确保数据在扫描完成后及时可用。
- `BlockStatsService` 与 `MigrationBlockController` 直接合作，对应 API 层的列表/详情接口。

## 扩展建议
- 若需要新增指标（例如按项目、组件维度拆分），可在 `CategoryLabelResolver` 中维护映射，并在 `MetricsAggregator` 中补充统计逻辑。
- 可以在 `MetricsAggregator` 中引入可配置的分页大小或多线程聚合，优化大规模数据场景。
- 若要提供导出能力，可在 `BlockStatsService` 中增加流式迭代接口，避免一次性加载所有代码块。

