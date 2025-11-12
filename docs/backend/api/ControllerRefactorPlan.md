# 接口改造计划（API 层）

## 背景

旧版控制器分散在 `/coverage`、`/api/diffs` 与 `/api/migrations` 下，接口粒度与返回结构均与最新前端需求不符。为支撑 `scanFull`、`scan`、`detail` 以及迁移三段式操作，需要统一 API 形态并复用现有领域服务。

## 目标接口

- `POST /api/scan/full`：触发全量扫描，返回汇总数据与差异矩阵。
- `POST /api/scan`：触发增量扫描，返回同样的数据结构。
- `POST /api/scan/detail`：根据 `filePath`（可选 `taskId`）返回 oracle 与 gauss 的差异详情与统计。
- `POST /api/migrate/generate`：生成迁移建议，支持可选 `options`。
- `POST /api/migrate/apply`：应用迁移结果，返回日志标识。
- `POST /api/migrate/revert`：撤销迁移结果。

响应采用统一包装：

```json
{
  "status": "success | failure",
  "message": "可选提示",
  "data": { ... }
}
```

## 控制器调整方案

1. **扫描接口聚合**
   - 新建 `ScanController` 统一路由 `/api/scan`。
   - 引入 `ScanRequestDTO`、`ScanResponseDTO`、`DiffMatrixItemDTO` 等结构。
   - 复用 `DiffAppService`、`CoverageAppService`，必要时增加组合应用服务。
   - 移除 `/coverage/analyze`、`/coverage/{taskId}` 等旧接口。
2. **单文件详情**
   - 通过 `POST /api/scan/detail` 返回 `oracleDiff`、`gaussDiff`、`migrationDiff`、覆盖率与统计。
   - 新增 `DiffDetailResponseDTO`、`DiffStatsDTO` 等数据结构。
3. **迁移接口**
   - `MigrationController` 统一调整为 `/api/migrate/{action}`。
   - `generate` 返回 `data.migrationDiff`，`apply` 返回 `logId`，`revert` 仅返回状态。
   - 替换旧版 DTO 与映射逻辑。

## DTO 与映射

- 新增 `ApiResponse<T>` 统一响应封装。
- 扩展 `DiffRequestDTO` 支持 `includeWorkingTree` 等配置。
- 新增 `ScanRequestDTO`、`ScanResponseDTO`、`DiffMatrixItemDTO`、`DiffDetailResponseDTO`、`MigrationGenerate/Apply` 系列 DTO。
- 新增 `ScanMapper`，重写 `MigrationMapper`。

## 应用层配合

- 新增 `ScanAppService` 负责 orchestrate 扫描、覆盖率、迁移建议以及缓存结果。
- 引入 `ScanResultStore` 缓存 `ScanReport`，支撑详情与迁移操作。
- 为迁移流程包装 `MigrationOperationResult`，桥接扫结果与 `MigrationAppService`。
- `CoverageAppService` 新增重载以直接处理 `DiffSummary` 列表。

## 实施步骤

1. 梳理并删除旧控制器路由及 DTO。
2. 定义统一响应模型与新 DTO/Mapper。
3. 新增或重构应用服务方法，补充所需缓存与聚合逻辑。
4. 实现新控制器并适配应用服务。
5. 更新文档，明确接口入参/出参。

---

## 实施结果（2025-10-30）

- **统一响应**：`ApiResponse<T>` 已落地，所有扫描与迁移接口均返回 `status/message/data`。
- **核心服务**：`ScanAppService` 聚合 Diff、Coverage、Generate、Migration 服务，`ScanResultStore` 支撑详情/迁移复用。
- **扫描接口**：`POST /api/scan/full` 与 `POST /api/scan`（增量模式自动包含工作区），返回 `ScanResponseDTO`（含 `taskId`、`summary`、`diffMatrix`）。
- **详情接口**：`POST /api/scan/detail` 支持可选 `taskId`，响应含 oracle/gauss diff、覆盖率、行级统计与自动生成的迁移模板。
- **迁移接口**：`/api/migrate/generate|apply|revert` 全部上线；generate/apply 返回数据载荷，revert 返回状态提示。
- **旧接口下线**：移除 `CoverageController`、`DiffController` 及旧版迁移 DTO，`MigrationController` 改造为新路由。
