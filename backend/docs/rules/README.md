# Rules 模块说明

## 模块职责
- 统一管理迁移规则的加载、缓存与执行，为 diff、标签过滤、内容脱敏等场景提供策略支持。
- 提供规则评估报告，便于前端标记命中情况或提示用户处理建议。

## 关键组件
- `backend/src/main/java/com/example/codecompare/rebuild/rules/RuleLoader.java`  
  - 负责从 `rules.yaml` 读取规则列表，支持 `classpath:` 与文件路径。  
  - 使用 `AtomicReference` 缓存 `RuleSet`，提供 `reloadWithReport` 返回结构化结果。
- `backend/src/main/java/com/example/codecompare/rebuild/rules/RuleRegistry.java`  
  - 将规则 ID 映射到 `RuleStrategy`，并负责执行顺序与命中记录。  
  - 默认注册由 `RulesAutoConfiguration` 装配的策略集合。
- `backend/src/main/java/com/example/codecompare/rebuild/rules/RuleEvaluationFacade.java`  
  - 面向业务层的门面，包装 `RuleRegistry`，输出 `RuleEvaluationReport` 和当前规则集。
- `backend/src/main/java/com/example/codecompare/rebuild/rules/strategy`  
  - `AbstractRuleStrategy`：封装通用解析与命中记录逻辑。  
  - `BlockFilterRuleStrategy`：根据标签或文件模式过滤 diff 块。  
  - `ContentMaskRuleStrategy`：供 diff 模块脱敏使用。  
  - `FieldReplaceRuleStrategy`、`PresenceRuleStrategy`、`SimilarityRuleStrategy` 等提供字段替换、存在性检测或相似度判定。

## 规则格式
默认规则文件 `config/rules.yaml` 结构示例：
```yaml
rules:
  - id: mask-credentials
    type: content-mask
    params:
      pattern: "(?i)secret\\s*=\\s*['\\\"]?(\\w+)"
      replacement: "secret=***"
      applyTo: ["SOURCE", "TARGET"]
```
- `type` 对应策略实现，`params` 字段由策略自行解析。
- `RuleDefinition` 会将 `params` 保留为 `Map` 以供策略使用，可通过 `getStringParam`、`getBooleanParam` 等便捷方法读取。

## 生命周期与刷新
- `ApplicationProperties.getRules().isReloadOnChange()` 为 true 时，`RuleLoader` 在每次访问时都会尝试重新加载文件。
- `ConfigurationRefreshCoordinator` 会在用户触发刷新时执行 `RuleLoader#reloadWithReport`，并将结果展示在仪表盘。

## 扩展建议
- 新增规则类型时，需要：  
  1. 在 `strategy` 包内实现 `RuleStrategy`；  
  2. 在 `RulesAutoConfiguration` 中注册 Bean；  
  3. 更新文档与默认 `rules.yaml` 示例。  
- 建议为规则添加 `displayName` 供前端展示，并通过 `RuleEvaluationReport` 返回命中说明。
- 若规则执行对性能影响较大，可以在策略内部使用缓存或预编译结构（例如正则 Pattern 缓存）。

