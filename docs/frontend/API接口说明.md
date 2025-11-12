# ΔO vs ΔG 对比平台 · 前端接口说明

本文档总结了前端当前接入的全部 REST 接口，便于后端实现对接。除非特别说明，请求与响应均使用 `application/json`。

---

## 通用约定

| 约定项 | 说明 |
| ------ | ---- |
| Base URL | `/api` |
| 时间格式 | 建议使用 `YYYY-MM-DD HH:mm:ss`（24 小时制） |
| 响应结构 | 推荐 `{ status: 'success' | 'error', message: string, data?: any }`<br/>若返回列表或对象也可直接返回业务数据，只要与下文约定一致 |
| 错误信号 | 失败时返回 `status: 'error'` 并给出 `message`，HTTP `4xx/5xx` 亦可 |

---

## 1. 仓库配置

### 1.1 保存仓库配置

- **Method**：`POST /api/config/save`
- **请求体**

```json
{
  "oracleRepoPath": "D:/repos/oracle",
  "gaussRepoPath": "D:/repos/gauss",
  "oracleBranches": {
    "source": "o1-main",
    "target": "o2-release"
  },
  "gaussBranches": {
    "source": "g1-develop",
    "target": "g2-release"
  },
  "operators": ["Alice", "Bob", "Carol"]
}
```

- **成功响应**

```json
{
  "status": "success",
  "message": "配置已保存",
  "data": {
    "oracleRepoPath": "...",
    "gaussRepoPath": "...",
    "oracleBranches": { "source": "...", "target": "..." },
    "gaussBranches": { "source": "...", "target": "..." },
    "operators": ["..."]
  }
}
```

> 前端会将 `data` 直接写入 Vuex，字段保持一致即可。

### 1.2 测试仓库连接

- **Method**：`GET /api/config/test`
- **请求 Query（可选）**

| 参数 | 说明 |
| ---- | ---- |
| `oracleRepoPath` | Oracle 仓库路径 |
| `gaussRepoPath` | Gauss 仓库路径 |

- **成功响应**

```json
{
  "status": "success",
  "message": "连接测试成功"
}
```

失败时建议返回：

```json
{
  "status": "error",
  "message": "Gauss 仓库未找到"
}
```

---

## 2. 差异扫描 / 文件对比

### 2.1 差异矩阵与概览

- **Method**：`GET /api/diff/scan`
- **成功响应**

```json
{
  "status": "success",
  "data": {
    "summary": {
      "totalFiles": 42,
      "oracleOnly": 12,
      "gaussOnly": 9,
      "matched": 21,
      "consistencyRate": 0.76
    },
    "diffMatrix": [
      {
        "filePath": "/src/service/UserService.java",
        "oracleDelta": "+5/-3",
        "gaussDelta": "+2/-1",
        "status": "partial"          // matched | oracle-only | gauss-only | partial | pending
      },
      {
        "filePath": "/src/config/AppConfig.java",
        "oracleDelta": "+0/-0",
        "gaussDelta": "+3/-0",
        "status": "gauss-only"
      }
    ]
  }
}
```

> `consistencyRate` 取值 0-1，前端以百分比显示。`status` 用于渲染不同颜色的标签。

### 2.2 获取单个文件的差异详情

- **Method**：`GET /api/diff/detail`
- **请求 Query**

| 参数 | 必填 | 说明 |
| ---- | ---- | ---- |
| `filePath` | 是 | 与矩阵中的 `filePath` 保持一致 |

- **成功响应**

```json
{
  "status": "success",
  "data": {
    "filePath": "/src/service/UserService.java",
    "module": "service",
    "oracleDiff": {
      "before": "public void addUser(User user) {...}",
      "after": "public void createUser(User user) {...}"
    },
    "gaussDiff": {
      "before": "public void addUser(User user) {...}",
      "after": "public void saveUser(User user) {...}"
    },
    "migrationDiff": "public void migrateUser(...) {...}",
    "stats": {
      "oracleAdded": 5,
      "oracleRemoved": 3,
      "gaussAdded": 2,
      "gaussRemoved": 1
    }
  }
}
```

> `oracleDiff` / `gaussDiff` 的 `before` / `after` 字段将直接用于 Monaco Diff Viewer。`migrationDiff` 可为空。

---

## 3. 迁移操作

迁移按钮均会携带当前 `filePath` 调用接口，后端根据实际业务编排逻辑。

### 3.1 生成迁移结果

- **Method**：`POST /api/migrate/generate`
- **请求体**

```json
{
  "filePath": "/src/service/UserService.java",
  "options": {
    "ignoreWhitespace": false,
    "ignoreComments": true
  }
}
```

> `options` 为可选字段，前端目前未使用，可按需扩展。

- **成功响应**

```json
{
  "status": "success",
  "message": "迁移文件已生成",
  "data": {
    "migrationDiff": "public void migrateUser(...) {...}"
  }
}
```

前端会读取 `data.migrationDiff` 更新 Diff 视图。若返回整体文件内容也可按需扩展。

### 3.2 应用迁移结果

- **Method**：`POST /api/migrate/apply`
- **请求体**

```json
{
  "filePath": "/src/service/UserService.java"
}
```

- **成功响应**

```json
{
  "status": "success",
  "message": "迁移结果已应用",
  "data": {
    "logId": 1011
  }
}
```

### 3.3 撤销迁移结果

- **Method**：`POST /api/migrate/revert`
- **请求体**

```json
{
  "filePath": "/src/service/UserService.java"
}
```

- **成功响应**

```json
{
  "status": "success",
  "message": "迁移结果已撤销"
}
```

---

## 4. 操作日志

### 4.1 获取日志列表

- **Method**：`GET /api/logs/list`
- **请求 Query（可选）**

| 参数 | 说明 |
| ---- | ---- |
| `page` / `pageSize` | 如需分页可增加，仅前端目前未使用 |
| `type` | 过滤操作类型（`Scan` / `Generate` / `Apply` / `Revert` 等） |

- **成功响应**

```json
{
  "status": "success",
  "data": [
    {
      "id": 1,
      "type": "Scan",
      "filePath": "/src/service/UserService.java",
      "user": "Alice",
      "time": "2023-10-01 14:20:33",
      "duration": "120ms",
      "status": "success",
      "detail": "完成基线差异扫描。"
    },
    {
      "id": 2,
      "type": "Generate",
      "filePath": "/src/service/UserService.java",
      "user": "Bob",
      "time": "2023-10-01 15:11:02",
      "duration": "180ms",
      "status": "success",
      "detail": "生成迁移脚本。"
    }
  ]
}
```

> `status` 可取 `success` / `running` / `error`。`detail` 用于展开行展示。

---

## 5. 系统设置

### 5.1 获取设置

- **Method**：`GET /api/settings`
- **成功响应**

```json
{
  "status": "success",
  "data": {
    "autoApply": true,
    "defaultDiffMode": "deltaO",
    "ignoreEmptyLines": false,
    "ignoreComments": true,
    "theme": "dark",
    "notification": {
      "onGenerate": true,
      "onApply": true,
      "onFailure": true
    }
  }
}
```

> `defaultDiffMode` 枚举：`deltaO` / `deltaG` / `deltaCompare` / `migration`。

### 5.2 更新设置

- **Method**：`POST /api/settings/update`
- **请求体**：与 `GET /api/settings` 返回结构一致。

- **成功响应**

```json
{
  "status": "success",
  "message": "配置更新成功"
}
```

---

## 6. 其它说明

- 若后端实际接口存在分页/筛选等高级能力，可在现有响应结构外增加字段（例如 `total`, `page` 等），前端后续会逐步对接。
- 目前前端在 Mock 模式下不会校验 HTTP 状态码，但正式联调建议：成功返回 `200` / `201`，失败返回合适的 `4xx`/`5xx` 并附带 `message`。
- 如需扩展更多差异类型、日志字段或迁移选项，请在返回值中添加新字段，前端会进行兼容性开发后使用。
