# JGit MissingObjectException 修复方案

## 问题描述

在扫描g仓库时遇到JGit错误：
```
MissingObjectException: Missing unknown 9ba47eb693d034d60aee5791a0209b14f2e01822
```

## 根因分析

### 1. Git对象缺失
- 错误中的commit hash `9ba47eb693d034d60aee5791a0209b14f2e01822` 在.git/objects目录中不存在
- 这可能是由于仓库初始化过程中对象创建失败或损坏

### 2. 缓存问题
- 后端应用缓存了错误的仓库状态
- JGit的RepositoryPool缓存了损坏的仓库对象

## 解决方案

### 1. 重新初始化仓库
```powershell
# 清理并重新初始化迁移仓库
cd examples
powershell -ExecutionPolicy Bypass -File "setup-examples.ps1" -Mode migration -Clean
```

### 2. 清理应用缓存
```powershell
# 删除JGit缓存目录
Remove-Item -Path "C:\Users\TyMos\.migratediff" -Recurse -Force -ErrorAction SilentlyContinue
```

### 3. 配置优化
在 `application.properties` 中调整配置：
```properties
# 扩展时间范围确保包含所有提交
migratediff.presets[0].target.time-from=2025-11-01T00:00:00+08:00
migratediff.presets[0].target.time-to=2025-11-06T00:00:00+08:00

# 包含所有目标分支
migratediff.presets[0].target.ref-hint=refs/heads/g2,refs/heads/g1
```

## 验证步骤

### 1. 检查Git对象完整性
```bash
cd examples/g
git cat-file -t 9ba47eb693d034d60aee5791a0209b14f2e01822  # 应返回 "commit"
git cat-file -t e87a5f617bde3a33ae3ef4fd6b44c67408547d82  # 应返回 "commit"
```

### 2. 验证提交历史
```bash
git log --all --pretty=format:"%H %ad %s" --date=iso
```

预期输出：
```
e87a5f617bde3a33ae3ef4fd6b44c67408547d82 2025-11-05 09:30:00 +0800 seed g g2
9ba47eb693d034d60aee5791a0209b14f2e01822 2025-11-02 03:00:00 +0800 seed g g1
```

### 3. 测试扫描功能
- 重启后端服务
- 执行扫描任务
- 确认g库能够正确扫描

## 预防措施

### 1. 仓库完整性检查
在初始化脚本中添加Git对象验证：
```powershell
function Test-Repository-Integrity {
    param($RepoPath)
    
    Push-Location $RepoPath
    try {
        # 检查Git对象完整性
        $fsckResult = git fsck
        if ($LASTEXITCODE -ne 0) {
            throw "Git repository corruption detected in $RepoPath"
        }
        
        # 验证所有分支引用有效
        $branches = git branch --format='%(refname:short)'
        foreach ($branch in $branches) {
            $commit = git rev-parse $branch
            if ($LASTEXITCODE -ne 0) {
                throw "Invalid branch reference: $branch in $RepoPath"
            }
        }
        
        return $true
    } finally {
        Pop-Location
    }
}
```

### 2. 缓存管理
定期清理JGit缓存：
```properties
# 减少缓存时间以便快速发现问题
migratediff.performance.cache-repository-seconds=60
```

### 3. 错误处理增强
在SnapshotLocator中添加更好的错误处理：
```java
try {
    commit = revWalk.parseCommit(ObjectId.fromString(commitId));
} catch (MissingObjectException e) {
    log.warn("Missing object detected: {}, clearing cache and retrying", commitId);
    repositoryPool.clearCache(repoPath);
    // 重新初始化仓库
    repository = getRepository(repoPath, fetchIfMissing);
}
```

## 监控建议

### 1. 日志监控
监控以下关键日志：
- `MissingObjectException`
- `Parallel snapshot locator failed`
- `Repository scan failed`

### 2. 健康检查
添加仓库健康检查端点：
```java
@GetMapping("/health/repositories")
public ResponseEntity<Map<String, Object>> checkRepositoryHealth() {
    Map<String, Object> health = new HashMap<>();
    for (String repoPath : configuredRepos) {
        health.put(repoPath, checkRepositoryIntegrity(repoPath));
    }
    return ResponseEntity.ok(health);
}
```

## 相关文件

- **配置文件**: `backend/src/main/resources/application.properties`
- **初始化脚本**: `examples/setup-examples.ps1`
- **测试配置**: `examples/test-g-repo-config.yaml`
- **时间轴配置**: `examples/_fixtures/timeline.json`

## 修复状态

✅ **已完成**: Git对象缺失问题修复  
✅ **已完成**: 缓存清理  
✅ **已完成**: 配置优化  
✅ **已完成**: 验证测试  
⏳ **待完成**: 预防措施实施  

---

**修复时间**: 2025-11-21  
**修复版本**: v2.0  
**影响范围**: g仓库扫描功能  
**风险等级**: 中等（已修复）
