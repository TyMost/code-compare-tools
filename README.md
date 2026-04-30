# code-compare-tools（migratediff）

企业级代码仓库差异分析、覆盖率计算与迁移辅助工具。

## 功能特性

### 差异分析（Diff）
- 两套代码仓库（ΔO 与 ΔG）的文件级 / 块级差异对比
- 支持分支、Tag、时间窗口等多种快照定位策略
- 差异矩阵可视化，支持过滤与导出

### 覆盖率计算（Coverage）
- 基于代码块的精确覆盖率评估（Strong / Legacy 双算法）
- 可配置噪音过滤（import-only、comment-only 等）
- 完整文件上下文优化策略，提升匹配精度
- 支持 Excel / CSV 多仓批量报告导出

### 代码迁移（Migration）
- 差异块自动映射为迁移任务
- 支持人工决策（Accept / Reject / Skip）
- 迁移结果可追溯、可回滚

### Git 集成
- 支持 SSH 私钥认证（OpenSSH 格式）
- 支持分支策略：Release-Based / Time-Window / Snapshot
- 增量快照扫描，降低重复计算开销

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 8 + Spring Boot 2.7 + JGit 5.13 |
| 前端 | Vue 2 + Element UI + Monaco Editor |
| 构建 | Maven 3.8 + npm |
| 存储 | 文件系统持久化（JSON） |

## 快速开始

### 环境要求
- Java 8+
- Maven 3.8+
- Node.js 16+
- npm 8+

### 启动后端

```bash
cd backend
mvn spring-boot:run
```

API 默认端口：8081。

### 启动前端

```bash
cd frontend
npm install
npm run serve
```

前端默认地址：http://localhost:8080（开发服务器代理到 8081）。

### 本地配置（可选）

1. 复制本地配置模板：
```bash
cp backend/src/main/resources/application-local.properties.example \
   backend/src/main/resources/application-local.properties
```

2. 修改其中的仓库路径、时间窗口等本地参数。

### 运行测试

```bash
cd backend
mvn test
```

## 项目结构

- **backend/** - Spring Boot 后端（Java）
  - `api/` - REST Controller 层
  - `application/` - 应用服务层
  - `domain/` - 领域模型（Diff / Coverage / Migration / Repo）
  - `infrastructure/` - 基础设施（Git 适配器、文件系统持久化）
  - `shared/` - 工具类
- **frontend/** - Vue 2 前端
  - `src/api/` - API 调用封装
  - `src/components/` - 通用组件
  - `src/pages/` - 页面组件
  - `src/workers/` - Monaco Editor Workers
- **scripts/** - 构建与性能测试脚本
- **examples/** - 示例项目
- **output/** - 构建输出

## API 概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/diff | 计算两仓库差异 |
| GET | /api/diff/matrix | 获取差异矩阵 |
| POST | /api/coverage | 计算覆盖率 |
| GET | /api/coverage/algorithm | 查询可用算法 |
| POST | /api/migration/generate | 生成迁移任务 |
| POST | /api/migration/apply | 应用迁移结果 |
| POST | /api/scan | 扫描仓库快照 |
| GET | /api/export/{taskId} | 导出报告 |

## 主要配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| coverage.algorithm | strong | 覆盖率算法（strong / legacy） |
| coverage.filter.noise.enabled | true | 启用噪音过滤 |
| migratediff.scan.default-strategy | release-auto | 默认扫描策略 |
| migratediff.ssh.enabled | true | 启用 SSH 认证 |
| export.max.concurrent.tasks | 5 | 最大并发导出任务数 |

详细配置见 backend/src/main/resources/application.properties。

## License

MIT License - see [LICENSE](LICENSE) 文件。
