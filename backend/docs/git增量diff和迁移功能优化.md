# Git 增量 diff 与迁移功能优化方案

## 目标
- 在保留“源、目标各取最新快照”的全量对比能力的前提下，引入增量 diff 支撑增删改语义。
- 让前端详情页、后端 API 与迁移流程都能识别 ADD / DELETE / MODIFY。
- 兼容 JDK 8，无需对既有 JSON 结构做破坏性升级。

## 总体思路
1. **扫描阶段输出双模式**  
   - 全量模式：维持现有 `sourceContent` / `targetContent` 字符串对，细节完全不变。  
   - 增量模式：额外生成结构化的 `DiffSegment` 列表，记录操作类型、行号范围、源/目标文本。
2. **数据存储与 API**  
   - `ScanSummary` 增加 `diffMode`（`FULL` / `INCREMENTAL`），并透传到 `BlockDecisionSnapshot`、`CodeBlockDetailDTO`。  
   - 详情 API 返回 `diffSegments` 时保留旧字段不变，使旧版前端自动忽略新字段。
3. **前端渲染**  
   - `diffMode === 'full'` 时沿用 Monaco Diff。  
   - `diffMode === 'incremental'` 时逐段渲染（新增块仅显示目标文本、删除块仅显示源文本、修改块可内嵌 Monaco 或自定义对比）。
4. **迁移执行**  
   - 延续原有 `apply`/`generate` 行为，用 `diffMode` 决定执行策略：  
     - 全量模式仍做“旧串替换为新串”。  
     - 增量模式根据段类型执行插入、删除、替换，避免误删其他上下文。

## 模块改造重点
### 扫描与存储
- `ScanProperties` 增加 `mode` 配置项，默认 `FULL`，开启增量时置为 `INCREMENTAL`。  
- `ProjectDiffGenerator` 在增量模式下拆分 diff 片段，生成 `DiffSegmentDTO`：  
  ```java
  class DiffSegmentDTO {
      DiffSegmentType type; // ADD / DELETE / MODIFY
      int sourceStart;
      int sourceLineCount;
      int targetStart;
      int targetLineCount;
      List<String> sourceLines;
      List<String> targetLines;
      double similarity;
      Map<String, Object> metadata;
  }
  ```  
- 将 `DiffSegmentDTO` 序列化进 `BlockDecisionRecord.metadata`（如 `diffSegments` 数组），保留原始 `BlockDiff` 防止老版本出错。

### 服务层 DTO
- `CodeBlockDetailDTO` 新增 `diffMode`、`segments` 字段，并在 `BlockStatsService.findDetail` 中填充：  
  - 全量模式只回传旧字段；  
  - 增量模式同时返回 `detail.oldCode/newCode` 与 `detail.diffSegments`，便于前端过渡。
- `CodeBlockDetailView` 同步新增字段，以兼容 REST 输出。

### 前端
- Vuex `detail` state 扩展：  
  ```js
  detail: {
    oldCode,
    newCode,
    diffMode: 'full' | 'incremental',
    diffSegments: []
  }
  ```
- `CodeBlockDetail.vue` 根据 `diffMode` 切换：  
  - `full`：现有 `<code-diff-viewer>`。  
  - `incremental`：新建 `<incremental-diff-viewer>` 组件，支持分段展示和颜色标记。
- 列表页无需改动；若需提示增量模式，可在卡片上显示 `模式：INCREMENTAL`。

### 迁移服务
- `CodeBlockMigrationService` 中读取 `diffMode`：  
  - `FULL`：走当前 `replace` 流程。  
  - `INCREMENTAL`：将 `diffSegments` 映射为具体文件编辑操作（先按行排序，再执行删除→修改→新增，避免行号错位）。
- 引入轻量行编辑工具类，支持 JDK 8（纯 `StringBuilder`/`List<String>` 操作，不依赖 `java.nio.file.Files.writeString` 等新 API）。

## 兼容性策略
1. 先上线后端：新增字段默认为空，旧版前端忽略。  
2. 在配置文件中提供开关，只对试点项目开启 `mode=INCREMENTAL`。  
3. 待前端完成渲染组件后，再逐步推广增量模式。  
4. 保留全量扫描作为 fallback，遇到异常可快速回滚配置。

## 测试要点
- **单元测试**  
  - `ProjectDiffGenerator`：验证 ADD / DELETE / MODIFY 片段输出正确。  
  - `CodeBlockMigrationService`：分别对三类操作执行写回测试。  
  - DTO 序列化测试，确保缺省字段时 JSON 仍合法。
- **集成测试**  
  - 全量模式回归：数据与页面无差异。  
  - 增量模式：构造增删改场景，验证详情页片段渲染与迁移结果。  
  - 混合场景：部分文件增量、部分文件无变化，算法能自动忽略无 diff 的文件。

## 后续计划
- 引入操作级审计（记录每段 diff 的处理动作）。  
- 结合 AI 建议时，以 `DiffSegment` 为粒度生成推荐。  
- 若需求进一步复杂，可考虑将 `diffSegments` 存储成独立表／文件，减少对 `metadata` 的依赖。  

以上方案确保原有全量对比功能零改动，同时为增量 diff 与迁移提供一条清晰的升级路径。
