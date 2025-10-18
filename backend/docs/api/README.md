# API 模块说明

## 模块职责
- 以 REST API 形式向前端提供代码块列表、仪表盘总览与迁移操作能力。
- 统一请求参数解析、响应包装与异常转换，保证前端调用体验一致。
- 将业务服务的领域对象转换为视图 DTO，隐藏仓储实现细节。

## 控制器与端点
| HTTP | 路径 | 说明 | 关键依赖 |
| --- | --- | --- | --- |
| GET | `/api/v1/migration/overview` | 仪表盘概览，可携带 `refresh=true` 触发重新扫描与配置刷新。 | `DashboardSummaryService`、`ConfigurationRefreshCoordinator`、`FileScanService` |
| GET | `/api/v1/migration/code-blocks` | 代码块分页列表，支持分类过滤、文件过滤。 | `BlockStatsService`、`MigrationViewMapper` |
| GET | `/api/v1/migration/code-blocks/{id}` | 单个代码块详情，按 `blockId` 查询。 | `BlockStatsService`、`MigrationViewMapper` |
| POST | `/api/v1/migration/code-blocks/generate` | 批量生成注解副本。 | `LocalAgentOrchestrator.generateAnnotatedCopies` |
| POST | `/api/v1/migration/code-blocks/apply` | 批量应用注解副本。 | `LocalAgentOrchestrator.applyAnnotatedCopies` |
| POST | `/api/v1/migration/code-blocks/undo` | 批量撤销注解副本。 | `LocalAgentOrchestrator.revertAnnotatedCopies` |
| POST | `/api/v1/migration/code-blocks/ignore` | 预留的忽略操作，当前返回提示。 | 占位实现 |
| GET | `/api/v1/migration/code-blocks/{id}/ai-suggestion` | 读取 AI 建议，占位逻辑返回“等待建设”。 | `MigrationViewMapper` |
| POST | `/api/v1/migration/agent/execute` | 触发本地 Agent，占位逻辑记录请求。 | 占位实现 |

所有端点均定义于 `backend/src/main/java/com/example/codecompare/rebuild/api/controller`，遵循 `/api/v1/migration` 前缀。

## DTO 与视图映射
- `backend/src/main/java/com/example/codecompare/rebuild/api/dto` 包含列表项、详情、仪表盘等视图模型，均为不可变对象。
- `backend/src/main/java/com/example/codecompare/rebuild/api/mapper/MigrationViewMapper.java` 负责将 `MetricsAggregator`、`CodeBlockDetailDTO` 等领域对象转换为 DTO，并补充提示信息或默认值。
- `AgentSuggestionView`、`CodeBlockListView`、`MigrationOverviewView` 等 DTO 均通过 `MigrationViewMapper` 构建，确保格式统一。

## 统一响应与异常处理
- `backend/src/main/java/com/example/codecompare/rebuild/api/response/ApiResponse.java` 定义统一响应结构；`ApiResponseFactory` 提供常用构造方法。
- `backend/src/main/java/com/example/codecompare/rebuild/api/handler/GlobalExceptionHandler.java` 捕获未处理异常，转换为 `ApiResponse`，并记录日志。
- 控制器在数据为空时返回 `ApiResponseFactory.ok` 携带提示语，前端可根据 `message` 展示通知。

## 参数处理与容错
- `MigrationBlockController` 的 `listBlocks` 支持数组参数两种写法（`categories` 与 `categories[]`），兼容不同前端框架。
- `normalizeProjectKey` 会过滤 `"null"`、`"undefined"` 等前端默认值，避免污染仓储查询。
- 批量操作在接收到空列表时直接返回错误提示，不触发服务端任务。

## 扩展建议
- 若需要引入鉴权，可在控制器上添加 `@PreAuthorize` 或编写 `HandlerInterceptor` 校验项目权限。
- 对于真实 Agent 接入，可以在 `/agent/execute` 增加任务队列或调用第三方服务，并将任务状态通过新端点暴露。
- 若要支持流式导出，可在 `MigrationBlockController` 新增 `GET /code-blocks/export`，直接复用 `BlockStatsService` 的分页查询。

