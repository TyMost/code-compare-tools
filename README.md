# code-compare-tools

一个用于比较两套代码仓库（ΔO 与 ΔG）差异、计算覆盖率并辅助迁移分析的工具，包含：

- `backend`：Spring Boot 后端，负责扫描、差异计算、覆盖率评估、导出。
- `frontend`：Vue2 前端，负责总览、文件对比、块级映射可视化。

## 1. 快速开始

## 1.1 环境要求

- Java 8+
- Maven 3.8+
- Node.js 16+
- npm 8+

## 1.2 启动后端

```bash
cd backend
mvn spring-boot:run
```

默认端口：`8081`。

### 本地配置建议

`backend/src/main/resources/application.properties` 已改为通用默认配置。

如需配置本机仓库路径与预置扫描参数：

1. 复制模板：

```bash
cp backend/src/main/resources/application-local.properties.example backend/src/main/resources/application-local.properties
```

2. 修改 `application-local.properties` 中的本地绝对路径与时间窗口。

## 1.3 启动前端

```bash
cd frontend
npm install
npm run serve
```

开发服务默认地址通常为 `http://localhost:8080`。

## 2. 常用命令

### 前端质量检查

```bash
cd frontend
npm run lint
```

### 后端测试

```bash
cd backend
mvn test
```

> 说明：如果当前网络无法访问 Maven 中央仓库，`mvn test` 可能失败（依赖下载受限）。

## 3. 当前项目约定

- 前端提交前至少保证 `npm run lint` 通过。
- 环境相关配置（仓库路径、私钥、时间窗口）优先放在本地配置，不要写入通用默认配置。
- Windows 下可使用 `backend/run-api.ps1` 进行本地 API 冒烟验证（默认使用 `http://localhost:8081`）。
