# 快照扫描日志测试指南

## 修改内容总结

我已经在以下文件中添加了详细的日志输出，用于显示时间范围内最早和最晚提交的hash值：

### 1. IncrementalSnapshotScanner.java
- 添加了 `logSnapshotDetails()` 方法
- 在快照扫描完成后打印详细的比较信息，包括：
  - 仓库路径
  - 时间范围
  - 最早提交hash和提交时间
  - 最晚提交hash和提交时间
  - 增量类型

### 2. SnapshotLocator.java
- 增强了引用收集过程的日志
- 添加了快照定位完成的详细信息日志，包括：
  - 扫描的引用数量
  - 最早提交的完整信息（hash、时间、来源引用）
  - 最晚提交的完整信息（hash、时间、来源引用）

### 3. application.properties
- 启用了远程引用扫描：`snapshot-include-remote-refs=true`
- 配置了专门的日志级别：
  ```properties
  logging.level.com.example.migratediff.infrastructure.git.IncrementalSnapshotScanner=INFO
  logging.level.com.example.migratediff.infrastructure.git.SnapshotLocator=INFO
  ```

## 测试步骤

### 1. 重启后端服务
```bash
cd backend
mvn spring-boot:run
```

### 2. 执行快照扫描
```bash
curl -X POST http://localhost:8081/api/scan/full \
  -H "Content-Type: application/json" \
  -d '{
    "presetName": "default-og-snapshot"
  }'
```

### 3. 查看日志输出
日志位置：`${user.home}/.migratediff/logs/application.log`

你应该能看到类似以下的输出：

```
=== 快照扫描结果详情 ===
仓库路径: D:\Coding\code-compare-tools\examples\o
时间范围: 2025-11-01T00:00:00Z 至 2025-11-30T23:59:59Z
最早提交 (基准): a1b2c3d4e5f6789012345678901234567890abcd
最早提交时间: 2025-11-05T10:30:15Z
最晚提交 (目标): b2c3d4e5f6789012345678901234567890abcde
最晚提交时间: 2025-11-25T14:22:33Z
增量类型: DELTA_O
========================

快照定位完成 - 扫描了 15 个引用
最早提交: hash=a1b2c3d4e5f6789012345678901234567890abcd, 时间=2025-11-05T10:30:15Z, 引用=refs/heads/o
最晚提交: hash=b2c3d4e5f6789012345678901234567890abcde, 时间=2025-11-25T14:22:33Z, 引用=refs/remotes/origin/o

总共收集到 25 个引用用于快照扫描 (包含远程: true, 包含标签: false)
```

## 关键信息说明

- **最早提交 (基准)**: 这是时间范围内找到的最早提交，将作为比较的基准点
- **最晚提交 (目标)**: 这是时间范围内找到的最晚提交，将作为比较的目标点
- **引用**: 显示该提交来自哪个Git引用（本地分支或远程分支）
- **hash值**: 完整的Git提交hash，你可以用这个值来验证具体的提交内容

## 验证提交信息

你可以使用以下命令验证具体的提交信息：

```bash
cd examples/o
git show a1b2c3d4e5f6789012345678901234567890abcd
git show b2c3d4e5f6789012345678901234567890abcde
```

## 故障排除

如果看不到预期的日志输出：

1. **检查日志级别**: 确保配置了正确的日志级别
2. **检查时间范围**: 确保配置的时间范围内确实有提交
3. **检查远程引用**: 确保已经fetch了远程分支并且`snapshot-include-remote-refs=true`
4. **检查仓库路径**: 确保仓库路径正确且包含.git目录

现在当你运行快照扫描时，就能清楚地看到系统正在比较哪两个具体的提交了！
