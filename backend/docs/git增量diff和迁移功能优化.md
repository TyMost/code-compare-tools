# Git 增量 diff 与迁移功能优化方案

## 目标
- 在保持“源、目标仓库各自使用最新快照”的现有全量比对流程不变的前提下，当 `migration.scan.diffEngine` 配置为 `git` 时启用增量 diff，显式区分新增、删除、修改。
- 让前端详情页、后端 API 以及迁移执行逻辑都能识别并正确处理 ADD / DELETE / MODIFY。
- 全程兼容 JDK 8，避免引入需要更高版本的语法或库。

## 总体思路
1. **双模式输出**  
   - `migration.scan.diffEngine: default`：继续生成 `sourceContent` 与 `targetContent` 字符串对，完全复用旧流程。  
   - `migration.scan.diffEngine: git`：在原有字符串对基础上，额外输出结构化 `DiffSegment` 列表，描述每段差异的操作类型与文本内容。
2. **API 与数据存储向后兼容**  
   - DTO、持久化模型保留旧字段，同时新增可选字段承载 `DiffSegment`。  
   - 当未开启增量模式时，这些新增字段默认缺失，旧版前端与接口调用无需调整即可运行。
3. **前端按模式渲染**  
   - 全量模式走 Monaco Diff。  
   - 增量模式按段渲染：新增段仅显示目标文本、删除段仅显示源文本、修改段可选择内嵌 Monaco 或定制化对比。
4. **迁移动作分类型执行**  
   - 全量模式保持“旧串替换为新串”。  
   - 增量模式将 `DiffSegment` 转换成按顺序执行的插入、删除、替换操作，避免误伤邻接代码。

## 模块改造重点

### 扫描与存储
- 以 `migration.scan.diffEngine` 作为开关：  
  ```yaml
  migration:
    scan:
      diffEngine: git  # 启用增量 diff；保持 default 时仍走全量快照
  ```  
- `ProjectDiffGenerator` 在 `diffEngine=git` 时拆分 diff 片段，构建 `DiffSegmentDTO`（类型、起止行、源/目标行列表、相似度、元数据等）。  
- 将 `DiffSegmentDTO` 序列化至 `BlockDecisionRecord.metadata.diffSegments`，同时保留原始 `BlockDiff` 字段，确保旧版逻辑仍可读取。
- `ScanSummary` 与 `BlockDecisionSnapshot` 增加 `diffMode` 字段（`FULL` / `INCREMENTAL`），用于前后端判定当前块的渲染与迁移策略。

### 服务层 DTO
- `CodeBlockDetailDTO`/`CodeBlockDetailView` 新增：  
  - `diffMode`：`full` 或 `incremental`。  
  - `diffSegments`：`List<DiffSegmentView>`，仅在增量模式下填充。  
  - 保留 `oldCode`/`newCode` 字段，以便前端在未适配前回退到旧渲染方式。
- 统计类接口可追加增量模式特有的指标（如新增/删除行数），同时保证默认值为 0。

### 前端
- Vuex `detail` state 新增 `diffMode`、`diffSegments` 字段；旧字段保持不变。  
- `CodeBlockDetail.vue` 根据 `diffMode`：  
  - `full`：继续渲染 `<code-diff-viewer>`。  
  - `incremental`：渲染 `<incremental-diff-viewer>`，支持按段折叠、颜色标记和操作提示。  
- 允许在 UI 上展示当前 diff 引擎，例如在详情页或统计卡片中加上“模式：GIT”。

### 迁移服务
- `CodeBlockMigrationService` 根据块的 `diffMode` 分支：  
  - `FULL`：沿用现有替换逻辑。  
  - `INCREMENTAL`：读取 `diffSegments`，按源文件行号顺序先执行删除、再做修改、最后插入，确保行号偏移被正确处理。  
- 引入基于 `List<String>` 的行级编辑工具（JDK 8 友好），确保重复执行时仍然得到幂等结果。

## 兼容性策略
1. **后端先行**：发布后端改造版本，`diffSegments` 默认缺失，旧前端无需变更即可运行。  
2. **灰度开启**：在试点环境仅对指定项目配置  
   ```yaml
   migration:
     scan:
       diffEngine: git
   ```  
   验证增量流程；其他项目保持 `default`。  
3. **前端联调**：完成新组件渲染后再逐步扩大启用范围。  
4. **快速回退**：若出现问题，可立即将配置恢复为 `default`，自动回归全量模式。

## 测试要点
- **单元测试**  
  - `ProjectDiffGenerator`：验证在 `git` 模式下正确拆分 ADD / DELETE / MODIFY。  
  - `CodeBlockMigrationService`：针对三类操作分别校验文件写回结果。  
  - DTO 序列化/反序列化：确保新增字段缺省时 JSON 仍合法。
- **集成测试**  
  - 全量模式回归：确认配置为 `default` 时所有功能与旧版本一致。  
  - 增量流程：构造包含新增、删除、修改的场景，验证详情页展示、迁移执行与统计数据。  
  - 混合场景：同一批扫描中部分文件无变化、部分文件增量，确保算法能正确跳过未变动文件。

## 后续计划
- 记录 `DiffSegment` 级别的操作日志，为后续审计与回滚提供依据。  
- 将 AI 建议与 `DiffSegment` 结合，提供针对单段代码的提示或自动修复。  
- 若数据量持续增长，可考虑将 `diffSegments` 独立存储，减轻 `metadata` 负担。

通过上述设计，在不影响原有全量流程的同时，只需在 `migration.scan.diffEngine: git` 开关下即可获得增量 diff 与迁移的完整能力，并保持配置可快速回退。
