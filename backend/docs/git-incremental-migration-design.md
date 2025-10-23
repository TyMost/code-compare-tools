# 增量 Git Diff 与迁移能力优化设计

> 运行环境：JDK 8、Vue 2.7。本文档总结前期讨论结论，形成可执行的设计与实施计划，方便分阶段落地。

## 1. 背景与目标

- 现状：`scan.diffEngine=git` 复用了默认 Diff/规则/迁移逻辑，出现以下痛点：
  - Diff 层缺少原始 `+ / -` 行信息，跨库“源删 → 目标增”的场景易被误判为未迁移；
  - 规则体系高度依赖 similarity/presence，配置复杂且与 Git 行为不完全匹配；
  - 迁移模块在模板选择、原始代码回填等环节需要大量 fallback，带来重复写入、撤销异常；
  - 多处代码以 `if (diffEngine)` 分支方式耦合两种引擎，后续扩展成本高。
- 目标：
  1. 给增量 Git Diff 提供一套“保真 + 轻状态”的数据结构；
  2. 将规则与迁移逻辑简化到最小依赖，保证标签判定与模板选择可预测；
  3. 通过策略模式等手段，让 Git 方案与 default 引擎逻辑解耦；
  4. 确保 default 引擎向后兼容，增量迭代风险可控。

## 2. 设计原则

1. **最小状态**：Diff 只暴露原始行与枚举型变化类型（`SOURCE_ONLY`, `TARGET_ONLY`, `BOTH`），避免在模块间传递复杂布尔组合；
2. **新增字段 + 旁路扩展**：先扩充 `BlockDiff` 字段，不立刻移除旧属性，保证历史快照可读；
3. **策略化分层**：Diff 引擎 / 标签引擎 / 迁移服务采用接口 + 实现，运行时按 `scan.diffEngine` 注入；
4. **渐进式重构**：每个阶段都可编译运行，可随时回滚；default 引擎行为保持一致；
5. **前后端协同**：前端标签展示、按钮状态改为依赖新的 `statusKey` 枚举，减少硬编码。

## 3. 架构调整概览

```text
+----------------------------+
| ScanProperties.diffEngine  |
+----------------------------+
            |
            v
+---------------------+       +--------------------+
| DiffEngineStrategy  |<----->| DiffSynchronization |
|  - DefaultStrategy  |       +--------------------+
|  - GitStrategy      |
+----------+----------+
           |
   BlockDiff(rawLines, rawType, ...)
           |
           v
+-------------------+        +----------------+
| LabelingEngine    |------->| statusKey enum |
|  - DefaultRules   |        +----------------+
|  - GitLightRules  |
+----------+--------+
           |
           v
+------------------------+
| MigrationService       |
|  - DefaultMigration    |
|  - GitIncrementalMigr. |
+------------------------+
```

## 4. 关键方案详情

### 4.1 Diff 模块扩展

- 在 `BlockDiff` 新增：
  - `List<String> rawDiffLines`：保留原始 `git diff` hunk 行，含 `+/-/ ` 前缀；
  - `RawChangeType rawType`：`SOURCE_ONLY`（仅 `-`）、`TARGET_ONLY`（仅 `+`）、`BOTH`（既有 `+` 又有 `-`）。
- `DiffResultAssembler` 在组装块时填充上述字段；对 default 引擎来说等价于“多携带两个字段”。
- `DiffSynchronizationService` 刷新时同样更新 `rawDiffLines/rawType`，不再尝试重新 label。
- 大文件/二进制：保留现有 size guard，超限时可省略 raw 行并标记 `rawType=BOTH`。

### 4.2 规则层最小化

- 引入 `LabelingEngine` 接口，并提供两套实现：
  1. `DefaultLabelingEngine`：沿用 `rules.yaml` 规则；
  2. `GitLabelingEngine`：使用固定映射（可由 YAML 配置，但结构极简）：
     | rawType      | 默认标签         | statusKey           |
     |--------------|------------------|---------------------|
     | SOURCE_ONLY  | 源删除待适配     | `source_only`       |
     | TARGET_ONLY  | 目标新增适配     | `target_only`       |
     | BOTH         | 源需适配（或比较结果） | `both_change` |
- 若需要识别“已迁移”，可在 `rawType=BOTH` 且 similarity>=100 时输出 `migrated`。
- 规则加载处（`RulesConfiguration` 或 `BlockDiffLabeler`）根据 `diffEngine` 注入对应实现，避免全局条件分支。

### 4.3 Git 专属迁移服务

- 新增 `GitIncrementalMigrationService`，实现接口 `MigrationService`/`CodeBlockMigrationFacade`。
- 核心逻辑：
  - `resolveTargetBaseline()`：直接从 `rawDiffLines` 中提取 `+` 行，作为适配模板的目标基线；
  - `resolveSourceBaseline()`：从 `-` 行提取源实现；
  - 模板策略：
    - `TARGET_ONLY`：默认使用 `migrate_adapt`，原实现为空则自动退化为插入；
    - `SOURCE_ONLY`：生成标记提示（如保留源实现注释），可选是否插入目标文件；
    - `BOTH`：沿用现有适配模板；
  - 替换模式：用 `-` 组装出的原实现去做匹配，匹配失败时直接报错（不再静默插入），提示用户重新扫描。
- 尽量重用现有文件写入、锁管理、快照刷新等基础设施。

### 4.4 default 引擎兼容性

- 所有新增字段保持可选，旧快照无 `rawType` 时自动回退到 `BOTH`，保证不抛异常；
- default 流程仍走旧的规则 + 迁移实现，不受影响；
- 日志中打印 `rawType` 以辅助排查，若 default 引擎未来需要，也可复用。

### 4.5 策略化改造

- 定义接口：
  ```java
  interface DiffEngineStrategy { DiffResult compute(...); }
  interface LabelingEngine { BlockDiff label(BlockDiff original); }
  interface MigrationService { MigrationOperationResult apply(...); }
  ```
- 在 Spring 配置中，根据 `ScanProperties.getDiffEngine()` 选择具体实现，并通过工厂或条件注解注入。
- 现有 `if ("git")` 分支逐步替换为调用上述接口，保持代码整洁。

## 5. 分阶段实施计划

> 模块互不阻塞，可并行开发，推荐按顺序推进以便验证。

### 模块 A：Diff 扩展（raw 行 + rawType）
- **目标**：为 `BlockDiff` 添加原始行字段，更新快照结构。
- **关键改动**：
  - `BlockDiff`、`DiffResultAssembler`、`DiffSynchronizationService`
  - 增加序列化/反序列化支持（JSON/YAML）
- **测试清单**：
  - default、git 两种模式运行扫描 → 快照可读；
  - BlockDiff 新字段存在且数据正确；
  - 大文件/二进制不导致内存异常。
- **Codex 提示词示例**：
  ```
  请在 BlockDiff 增加 rawDiffLines(List<String>) 和 rawType(RawChangeType) 字段，并确保 DiffResultAssembler 在构建 BlockDiff 时填充这些字段。保持现有字段向后兼容，使用 JDK 8 语法。
  ```

### 模块 B：Git 轻量标签引擎
- **目标**：基于 rawType 输出精简标签。
- **关键改动**：
  - 新增 `GitLabelingEngine` 类；
  - 调整 `BlockDiffLabeler` 装配逻辑；
  - 更新前端枚举（Vue 2.7）。
- **测试清单**：
  - git 模式下，“源删/目标增/替换”三种块对应预期标签；
  - default 模式标签不变。
- **Codex 提示词示例**：
  ```
  新增 GitLabelingEngine，实现 LabelingEngine 接口：根据 BlockDiff.rawType 返回对应的标签与 statusKey。并在 BlockDiffLabeler 中根据 diffEngine 选择实现。
  ```

### 模块 C：Git 迁移服务
- **目标**：实现 `GitIncrementalMigrationService`，依赖 rawDiffLines 进行模板选择与替换。
- **关键改动**：
  - 新增服务类及配置；
  - `CodeBlockMigrationService` 提取公共能力或改为接口；
  - 更新模板选择、替换流程。
- **测试清单**：
  - `TARGET_ONLY` 场景生成/应用无重复写入；
  - `SOURCE_ONLY` 支持生成撤销；
  - `BOTH` 行为与原逻辑一致；
  - 撤销按钮状态正确。
- **Codex 提示词示例**：
  ```
  创建 GitIncrementalMigrationService，实现 MigrationService 接口。使用 BlockDiff.rawDiffLines 中的 '+' 行作为目标基线，'-' 行作为原实现，优先使用 migrate_adapt 模板，匹配失败时抛出 IllegalStateException。
  ```

### 模块 D：策略化重构
- **目标**：用策略模式替换散落的 `if (diffEngine)` 分支。
- **关键改动**：
  - 定义 DiffEngineStrategy/LabelingEngine/MigrationService 接口；
  - 在配置层（如 ScanConfiguration）根据 `ScanProperties` 注入；
  - 清理旧的分支逻辑。
- **测试清单**：
  - default/git 两种模式全链路跑通；
  - Spring 配置加载无冲突。
- **Codex 提示词示例**：
  ```
  将 ScanConfiguration 中基于 diffEngine 的分支改为注册 DiffEngineStrategy Bean（Default、Git），通过工厂方法返回对应实现，消除硬编码 if。
  ```

### 模块 E：前端适配（可并行）
- **目标**：Vue 2.7 前端根据新的 statusKey 与标签渲染按钮状态、提示信息。
- **要点**：
  - 更新状态判断（ActionBar、列表）；
  - 如新增 rawType 展示，可在详情页直接渲染；
  - 确保旧状态仍兼容。
- **Codex 提示词示例**：
  ```
  调整 ActionBar.vue 的 disableGenerate/disableUndo 逻辑：根据 detail.statusKey（新增字段）判断。默认兼容旧值。
  ```

## 6. 验收与回滚

1. 模块 A~E 均具备单独回滚能力（恢复文件即可），每个阶段完成后进行冒烟；
2. Git 模式回归流程：扫描 → 生成 → 应用 → 撤销；
3. default 模式至少跑一次全量扫描，确认标签、迁移无回归；
4. 若发现严重问题，可在配置中临时回退到 default 引擎，或回滚对应模块代码。

## 7. 后续展望

- 在 rawDiffLines 基础上，可实现前端差异可视化、导出等高级特性；
- 若未来支持第三种 diff 引擎（如 AI 辅助），仅需新增策略实现；
- 可进一步将规则引擎抽象成 DSL，方便运维自定义。

---

**备注**：文中示例模板、枚举名称可在实施阶段根据实际命名规范微调。请在每个阶段完成后同步更新此文档，以便追踪改造进度。
