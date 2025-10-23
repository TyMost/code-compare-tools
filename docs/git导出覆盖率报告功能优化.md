# Git 导出覆盖率报告功能优化设计

## 目标
- 在现有 Git 对比批量导出能力上补充“覆盖率”指标，用于衡量源仓库迁移到目标仓库时的覆盖程度。
- 同步提升 Web 页面与 Excel 报表的展示效果，让业务在在线与离线场景都能同时看到相似度与覆盖率。
- 全流程保持对 JDK 8 的兼容，避免引入更高版本语法或依赖。

## 指标定义
- **Coverage A→B**：源仓库增量改动中，被目标仓库增量改动覆盖的行数占源仓库全部增量行数的比例，仅统计真实变动行（不含 Git `sameLineCount`）。
- **Coverage B→A**：目标仓库相对于源仓库的对称指标，用于识别目标侧新增但未被源侧覆盖的改动。
- 相似度沿用既有算法：`(sameLineCount + 匹配块加权相似度) / (sameLineCount + 参与权重的行数)`。
- 当任一侧不存在增量时，覆盖率定义为 100%，并在界面给出提示以免误读。

## 方案概览
1. **比对阶段统计覆盖率**：在 `DualIncrementalComparisonCalculator` 中复用块匹配结果，累积各方向被覆盖的行数。
2. **API DTO 扩展**：为 `DualIncrementalComparisonView`、`GitComparisonFileView` 等响应对象新增覆盖率字段，保持对旧客户端的兼容。
3. **批量导出增强**：`GitComparisonBatchService` 汇总覆盖率指标，在 Excel 中新增相关列或表格。
4. **页面展示优化**：前端列表、详情页和统计视图展示覆盖率，与相似度并列并提供方向提示。
5. **回滚策略**：覆盖率计算默认启用，如需停用通过回滚版本或临时隐藏展示解决，无需额外开关。

## 后端设计

### 1. 块级比对与覆盖率计算
- 位置：`com.example.codecompare.rebuild.scanning.compare.DualIncrementalComparisonCalculator`
- 新增累计字段：`coveredLinesAtoB`、`sourceChangedLineSum`、`coveredLinesBtoA`、`targetChangedLineSum`。
- 计算流程：
  1. 对匹配成功的块，依据两侧 `getChangedLines()` 计算参考行数。
  2. 贡献公式：`coveredLinesAtoB += sourceChangedLines * (matchSimilarity / 100d)`，`coveredLinesBtoA += targetChangedLines * (matchSimilarity / 100d)`。
  3. 未匹配块只累计对应侧的 `ChangedLineSum`。
  4. 使用 `ratio(coveredLines, totalChanged)` 得到覆盖率，`ratio` 负责除数为 0 时返回 100%。
- 全程使用基本类型与 `Math` 方法，确保 JDK 8 兼容。

### 2. DTO 与序列化
- `DualIncrementalComparisonView` 新增 `coverageAtoB`, `coverageBtoA` 字段，Builder 中限制取值 0~100。
- 若需块级提示，可在 `DualIncrementalComparisonBlockView` 增补覆盖率贡献字段；若暂未需要可保持精简。
- `GitComparisonFileView` 在封装时透传覆盖率指标，旧字段维持不变。
- Jackson 默认忽略未知字段，老版本调用方仍可正常解析。

### 3. 汇总与统计
- `ProjectDiffGenerator` 可在内部上下文同步累计覆盖率，为后续规则或报表准备数据。
- `GitComparisonBatchService`：
  - 在 `computePairs` 中读取覆盖率字段写入 `FileRow`。
  - `RepositoryAggregate` 新增覆盖率相关统计，计算仓库级加权覆盖率。
  - Excel 输出持续复用现有四舍五入与格式化逻辑。

### 4. 配置管理
- 覆盖率作为核心能力随版本统一上线，不提供运行时开关。
- 特殊情况下可通过配置覆盖、隐藏前端字段或短期回滚代码临时禁用。

## 页面改造
- **文件列表**：增加“覆盖率”列，默认展示 `coverageAtoB`，悬停提示覆盖方向。
- **详情页**：在顶部信息块展示“相似度 / 覆盖率”，必要时在差异块旁提示覆盖贡献。
- **交互说明**：新增 tooltip 解释覆盖率公式及 A→B / B→A 的含义，避免误解。
- **视觉提示**：可沿用相似度的阈值，也可在 `rules.yaml` 新增覆盖率类规则，用于颜色或标签展示。

## Excel 导出调整
- `File Similarity` 工作表新增 `Coverage (A->B %)`、`Coverage (B->A %)` 两列，原有列顺延并更新 `autoSizeColumn` 范围。
- `Repository Similarity` 工作表补充覆盖率相关列，如 `Covered Lines`、`Coverage %`，区分源/目标角色。
- 若有需要，可增设 `Coverage Summary` 表按覆盖率排序展示文件或仓库。
- 继续使用 Apache POI 百分比格式（`0.00%`），保证 JDK 8 环境下的兼容。

## 兼容性与回滚
- 新增字段提供默认值或空值处理，旧版前端仍可正常渲染。
- Excel 列顺序调整需提前通知依赖报告的自动化脚本。
- 若覆盖率计算引发性能压力，可临时隐藏展示或回滚改动作为应急措施。

## 测试计划
- **单元测试**：新增 `DualIncrementalComparisonCalculatorTest`、`GitComparisonBatchServiceTest` 覆盖覆盖率计算与导出格式。
- **集成测试**：在实际迁移项目上跑对比，核对页面与 Excel 报告中的覆盖率与相似度一致性。
- **性能测试**：对大仓库或多项目组合验证覆盖率计算对导出耗时影响，可结合基准数据比对。

## 发布步骤
1. 实现覆盖率计算及 DTO 扩展，在测试环境验证正确性。
2. 更新前端展示逻辑，确保旧响应兼容。
3. 调整批量导出并生成样例报告给业务确认。
4. 更新 README/操作文档，说明覆盖率指标的含义与使用方法。
5. 上线后关注性能与业务反馈，必要时调整阈值或展示策略。

通过上述方案，可以在保持 JDK 8 兼容的同时，将覆盖率指标同时输出到 Web 页面与 Excel 报告，为迁移团队提供更直观的覆盖度衡量依据。
