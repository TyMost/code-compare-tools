# Core 模块说明

## 模块职责
- 提供跨模块共享的基础设施：配置属性、线程池、全局 `ObjectMapper`、项目根目录校验等。
- 管理应用生命周期事件，协调启动与关闭时的钩子行为。
- 为配置热刷新提供统一入口，保证规则、注解模板等资源的一致性。

## 关键配置与组件
- `backend/src/main/java/com/example/codecompare/rebuild/core/config/CoreAutoConfiguration.java`  
  - 注册全局 `ObjectMapper`（启用 `JavaTimeModule`）、`Clock`、`ThreadPoolTaskExecutor` 与 `BootstrapEventPublisher`。  
  - 根据 `ApplicationReadyEvent`、`ContextClosedEvent` 发布应用生命周期事件。
- `backend/src/main/java/com/example/codecompare/rebuild/core/properties/ApplicationProperties.java`  
  - 绑定 `migration.*` 配置：项目根目录、Git 对比开关、规则位置、输出路径。  
  - 支持 `project.roots` 旧格式与 `project.sources/targets` 新格式，`ProjectRootRegistry` 会进行冲突校验。
- `backend/src/main/java/com/example/codecompare/rebuild/core/support/ProjectRootRegistry.java`  
  - 负责路径归一化、合法性校验（防止越权访问）、源码/目标项目区分。  
  - 提供 `ensureInsideRoots`，供文件写入类保证操作安全。
- `backend/src/main/java/com/example/codecompare/rebuild/core/support/IgnorePatternMatcher.java`  
  - 基于 glob/正则的忽略匹配器，供 diff 与扫描模块过滤非关注文件。
- `backend/src/main/java/com/example/codecompare/rebuild/core/config/ConfigurationRefreshCoordinator.java`  
  - 统一刷新 `RuleLoader` 与 `AnnotationTemplateProvider`，生成 `ConfigurationReloadReport` 给前端展示。

## 应用生命周期
- `backend/src/main/java/com/example/codecompare/rebuild/core/event/BootstrapEventPublisher.java` 会在启动/关闭时发布 `ApplicationLifecycleEvent`，可用于埋点与运维监控。
- 若有自定义监听器，可直接订阅 `ApplicationLifecycleEvent` 或覆写 `BootstrapEventPublisher` 的行为。

## 配置刷新流程
1. 仪表盘请求 `refresh=true` 时，`MigrationDashboardController` 调用 `ConfigurationRefreshCoordinator#reloadAll`。
2. 协调器依次刷新规则与注解模板，并记录是否更新、来源路径、载入时间。
3. 刷新结果通过 `ConfigurationReloadReport` 回传前端，便于提示用户配置状态。

## 扩展建议
- 当需要新增全局配置时，优先在 `ApplicationProperties` 中声明，避免在各模块单独读取 `Environment`。
- 如需增加线程池，可在 `CoreAutoConfiguration` 中统一创建，避免任务堆积或线程泄露。
- 如果要支持动态项目根目录调整，可扩展 `ProjectRootRegistry`，添加监听器或热更新接口，但务必重新校验子目录冲突。

