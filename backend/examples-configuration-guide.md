# Examples仓库配置指南

## 概述

本文档说明了如何配置后端应用以支持examples文件夹中的Oracle和Gauss测试仓库。

## 配置文件位置

配置文件位于：`backend/src/main/resources/application.properties`

## 已配置的预设

### 1. default-og-snapshot (时间窗口快照模式)

这是默认启用的预设，用于扫描examples仓库中的时间窗口快照。

**源仓库配置 (Oracle)**：
```properties
migratediff.presets[0].source.code=o-g
migratediff.presets[0].source.path=D:\\Coding\\code-compare-tools\\examples\\o
migratediff.presets[0].source.scan-strategy=SNAPSHOT
migratediff.presets[0].source.time-from=2025-11-01T00:00:00+08:00
migratediff.presets[0].source.time-to=2025-11-30T23:59:59+08:00
migratediff.presets[0].source.ref-hint=refs/heads/o2
migratediff.presets[0].source.delta-type=DELTA_O
```

**目标仓库配置 (Gauss)**：
```properties
migratediff.presets[0].target.code=g
migratediff.presets[0].target.path=D:\\Coding\\code-compare-tools\\examples\\g
migratediff.presets[0].target.scan-strategy=SNAPSHOT
migratediff.presets[0].target.time-from=2025-11-01T00:00:00+08:00
migratediff.presets[0].target.time-to=2025-11-30T23:59:59+08:00
migratediff.presets[0].target.ref-hint=refs/heads/g2,refs/heads/g1
migratediff.presets[0].target.delta-type=DELTA_G
```

### 2. default-og-branch (分支对比模式 - 已注释)

这是备用的分支对比预设，当前处于注释状态。如需启用，请取消相关注释。

## 配置参数说明

### 基础参数

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `name` | 预设名称 | `default-og-snapshot` |
| `code` | 仓库代码 | `o-g` (源), `g` (目标) |
| `path` | 仓库路径 | `D:\\Coding\\code-compare-tools\\examples\\o` |
| `scan-strategy` | 扫描策略 | `SNAPSHOT` (时间窗口), `BRANCH` (分支对比) |
| `delta-type` | 增量类型 | `DELTA_O` (Oracle), `DELTA_G` (Gauss) |

### 时间窗口参数

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `time-from` | 开始时间 | `2025-11-01T00:00:00+08:00` |
| `time-to` | 结束时间 | `2025-11-30T23:59:59+08:00` |
| `ref-hint` | 引用提示 | `refs/heads/o2` |

### 分支参数 (BRANCH模式)

| 参数 | 说明 | 示例值 |
|------|------|--------|
| `branch-from` | 起始分支 | `refs/heads/o1` |
| `branch-to` | 目标分支 | `refs/heads/o2` |

### 其他参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `include-working-tree` | 包含工作树 | `false` |
| `fetch-if-missing` | 缺失时获取 | `true` |
| `remote-name` | 远程名称 | `origin` |
| `snapshot-include-remote-refs` | 快照包含远程引用 | `false` |
| `snapshot-include-tags` | 快照包含标签 | `false` |
| `snapshot-max-refs` | 快照最大引用数 | `256` |

## 使用方法

### 1. 启动后端服务

```bash
cd backend
mvn spring-boot:run
```

服务将在 `http://localhost:8081` 启动。

### 2. 使用API进行扫描

#### 完整扫描 (使用预设)

```bash
curl -X POST http://localhost:8081/api/scan/full \
  -H "Content-Type: application/json" \
  -d '{
    "presetName": "default-og-snapshot"
  }'
```

#### 自定义扫描

```bash
curl -X POST http://localhost:8081/api/scan/full \
  -H "Content-Type: application/json" \
  -d '{
    "oracle": {
      "repoPath": "D:\\Coding\\code-compare-tools\\examples\\o",
      "branch": "main",
      "scanStrategy": "SNAPSHOT",
      "timeFrom": "2025-11-01T00:00:00+08:00",
      "timeTo": "2025-11-30T23:59:59+08:00"
    },
    "gauss": {
      "repoPath": "D:\\Coding\\code-compare-tools\\examples\\g",
      "branch": "main",
      "scanStrategy": "SNAPSHOT",
      "timeFrom": "2025-11-01T00:00:00+08:00",
      "timeTo": "2025-11-30T23:59:59+08:00"
    }
  }'
```

### 3. 获取文件详情

```bash
curl -X POST http://localhost:8081/api/scan/detail \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "your-task-id",
    "filePath": "src/main/java/com/example/entity/User.java"
  }'
```

### 4. 获取提交历史

```bash
curl -X POST http://localhost:8081/api/scan/commit-history \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": "your-task-id",
    "filePath": "src/main/java/com/example/service/UserService.java"
  }'
```

## 路径配置

### Windows路径
当前配置使用Windows路径格式：
```properties
migratediff.presets[0].source.path=D:\\Coding\\code-compare-tools\\examples\\o
migratediff.presets[0].target.path=D:\\Coding\\code-compare-tools\\examples\\g
```

### Linux/macOS路径 (如需要)
```properties
migratediff.presets[0].source.path=/home/user/code-compare-tools/examples/o
migratediff.presets[0].target.path=/home/user/code-compare-tools/examples/g
```

## 时间配置

当前配置的时间窗口为：
- **开始时间**: 2025-11-01 00:00:00 (UTC+8)
- **结束时间**: 2025-11-30 23:59:59 (UTC+8)

**最新更新**: 2025-11-24 - 已将时间窗口扩展至11月30日，确保覆盖所有examples仓库的提交记录。

这个时间范围覆盖了整个11月，确保包含examples仓库中的所有提交。

## 分支配置

Examples仓库包含以下分支：
- **Oracle仓库**: `main`, `o1`, `o2`
- **Gauss仓库**: `main`, `g1`, `g2`

当前配置使用：
- Oracle: `refs/heads/o2`
- Gauss: `refs/heads/g2,refs/heads/g1`

## 性能优化配置

配置文件已启用以下性能优化：

```properties
# Repository缓存池
migratediff.performance.enable-repository-pool=true
migratediff.performance.repository-pool-size=10

# RevWalk池
migratediff.performance.enable-revwalk-pool=true
migratediff.performance.revwalk-pool-size=5

# 并行处理
migratediff.performance.parallel-scan=true
migratediff.performance.parallel-ref-processing=true
migratediff.performance.max-concurrent-refs=8

# 性能监控
migratediff.performance.monitoring.enabled=true
```

## 故障排除

### 1. 路径问题

**错误**: `Repository not found`
**解决**: 检查路径是否正确，确保examples仓库已初始化

```bash
cd examples
bash init_repos.sh
```

### 2. 时间范围问题

**错误**: `No commits found in time range`
**解决**: 调整时间范围，确保包含提交时间

```properties
migratediff.presets[0].source.time-from=2025-11-01T00:00:00+08:00
migratediff.presets[0].source.time-to=2025-11-30T23:59:59+08:00
```

### 3. 分支不存在

**错误**: `Branch not found`
**解决**: 检查分支引用是否正确

```bash
cd examples/o
git branch -a
cd ../g
git branch -a
```

## 验证配置

### 1. 检查配置加载

启动应用后查看日志：
```bash
tail -f ~/.migratediff/logs/application.log
```

### 2. 测试API连接

```bash
curl http://localhost:8081/api/health
```

### 3. 验证预设配置

```bash
curl http://localhost:8081/api/presets
```

## 扩展配置

### 添加新预设

在 `application.properties` 中添加新的预设：

```properties
migratediff.presets[2].name=custom-preset
migratediff.presets[2].source.path=/path/to/source/repo
migratediff.presets[2].target.path=/path/to/target/repo
# ... 其他配置
```

### 修改现有预设

直接修改对应预设的参数值，然后重启应用。

## 安全配置

配置文件包含SSH相关配置，但examples仓库为本地仓库，不需要SSH配置：

```properties
# SSH配置 (本地仓库不需要)
migratediff.ssh.enabled=false
```

## 监控和日志

### 启用详细日志

在 `application.properties` 中添加：

```properties
logging.level.com.example.migratediff=DEBUG
logging.file.path=${user.home}/.migratediff/logs
```

### 性能监控

性能监控已启用，可通过以下端点查看：

```bash
curl http://localhost:8081/api/performance/stats
```

---

**配置完成时间**: 2025-11-24 19:00  
**配置版本**: v1.0.0  
**适用版本**: Code Compare Tools v2.0+
