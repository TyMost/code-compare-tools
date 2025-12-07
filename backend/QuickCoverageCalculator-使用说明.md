# 快速覆盖率计算工具使用说明

## 📖 简介

`QuickCoverageCalculatorTest` 是一个专门用于快速计算Oracle和Gauss仓库之间代码覆盖率的单元测试工具。它使用您在 `application.properties` 中配置的Git仓库路径，可以快速分析指定文件的迁移覆盖率。

## 🚀 快速开始

### 1. 基本使用命令

```bash
# 进入后端目录
cd backend

# 计算单个文件覆盖率
mvn test -Dtest=QuickCoverageCalculatorTest#calculateSingleFileCoverage

# 计算多个文件覆盖率
mvn test -Dtest=QuickCoverageCalculatorTest#calculateMultipleFilesCoverage

# 计算所有Service文件覆盖率
mvn test -Dtest=QuickCoverageCalculatorTest#calculateAllServiceFilesCoverage

# 生成完整覆盖率报告
mvn test -Dtest=QuickCoverageCalculatorTest#generateCoverageReport
```

### 2. 配置要求

确保 `application.properties` 中有以下配置：

```properties
# Oracle仓库配置
migratediff.presets[0].source.code=o
migratediff.presets[0].source.path=D:\\Coding\\code-compare-tools\\examples\\o

# Gauss仓库配置  
migratediff.presets[0].target.code=g
migratediff.presets[0].target.path=D:\\Coding\\code-compare-tools\\examples\\g
```

## 📊 功能特性

### 🔍 支持的计算模式

1. **单文件分析** - 分析指定文件的覆盖率
2. **多文件分析** - 批量分析多个文件
3. **类型分析** - 按文件类型批量分析（如所有Service文件）
4. **全量分析** - 生成完整项目的覆盖率报告

### 📈 报告内容

#### 简单报告
- 总体覆盖率百分比
- 总行数和匹配行数
- 每个文件的详细覆盖率
- 未覆盖的代码块信息

#### 详细报告
- 覆盖率分布统计（高/中高/中等/中低/低）
- 低覆盖率文件列表（< 50%）
- 高覆盖率文件列表（≥ 80%）
- 完整的文件级别统计

## 🎯 使用场景

### 场景1：检查特定文件迁移质量
```bash
# 检查某个关键服务类的迁移情况
mvn test -Dtest=QuickCoverageCalculatorTest#calculateSingleFileCoverage
```

### 场景2：批量检查相关文件
```bash
# 检查某个模块的所有文件
mvn test -Dtest=QuickCoverageCalculatorTest#calculateAllServiceFilesCoverage
```

### 场景3：获取项目整体迁移状况
```bash
# 生成完整的项目覆盖率报告
mvn test -Dtest=QuickCoverageCalculatorTest#generateCoverageReport
```

## 📝 自定义文件路径

如果您想计算特定文件的覆盖率，可以修改测试方法中的文件路径：

```java
// 在 calculateSingleFileCoverage 方法中修改
String filePath = "src/main/java/com/example/你的包/你的文件.java";

// 或在 calculateMultipleFilesCoverage 方法中添加
List<String> filePaths = Arrays.asList(
    "src/main/java/com/example/你的文件1.java",
    "src/main/java/com/example/你的文件2.java"
);
```

## 🔧 故障排除

### 常见问题

1. **找不到文件错误**
   ```
   ⚠️ 在Oracle仓库中未找到指定文件
   ```
   - 检查文件路径是否正确
   - 确认文件在Oracle仓库中存在

2. **Git仓库访问错误**
   ```
   Invalid repository path; missing .git directory
   ```
   - 检查 `application.properties` 中的路径配置
   - 确认仓库已正确初始化

3. **空结果**
   ```
   找到Service文件数量: 0
   ```
   - 仓库中可能没有匹配的文件类型
   - 检查过滤条件和文件路径

### 调试技巧

1. **查看详细日志**
   ```bash
   mvn test -Dtest=QuickCoverageCalculatorTest#calculateSingleFileCoverage -X
   ```

2. **修改过滤条件**
   - 在代码中调整文件过滤条件
   - 临时移除某些过滤条件进行测试

## 📋 输出示例

### 成功的覆盖率报告
```
📊 覆盖率统计:
   总体覆盖率: 78.50%
   总行数: 150
   匹配行数: 118
   文件数量: 5

📋 文件详情:
   📄 src/main/java/com/example/service/UserService.java
      覆盖率: 85.20%
      总行数: 45
      匹配行数: 38
      未覆盖块: 2
        - 行15-20: MODIFY
        - 行35-37: ADD
```

### 完整报告示例
```
🎯 完整覆盖率报告
============================================================
📊 总体统计:
   总体覆盖率: 72.30%
   总行数: 200
   匹配行数: 145
   文件数量: 8

📈 覆盖率分布:
   81-100% (高): 2 个文件
   61-80% (中高): 3 个文件
   41-60% (中等): 2 个文件
   21-40% (中低): 1 个文件
   0-20% (低): 0 个文件

⚠️  低覆盖率文件 (覆盖率 < 50%):
   📄 src/main/java/com/example/config/DatabaseConfig.java - 35.50%
      未覆盖块: 3

✅ 高覆盖率文件 (覆盖率 >= 80%):
   📄 src/main/java/com/example/service/UserService.java - 85.20%
   📄 src/main/java/com/example/entity/User.java - 92.10%
============================================================
```

## 🎚️ 高级用法

### 1. 自定义覆盖率阈值
修改代码中的阈值条件：
```java
// 修改低覆盖率阈值
.filter(detail -> detail.getCoverage() < 0.3) // 改为30%

// 修改高覆盖率阈值  
.filter(detail -> detail.getCoverage() >= 0.9) // 改为90%
```

### 2. 添加新的文件类型过滤
```java
// 添加Controller文件过滤示例
List<String> controllerFiles = oracleSummary.getDiffFiles().stream()
    .filter(file -> file.getRelativePath() != null && 
                   file.getRelativePath().contains("/controller/") &&
                   file.getRelativePath().endsWith(".java"))
    .map(DiffFile::getRelativePath)
    .collect(Collectors.toList());
```

### 3. 导出报告到文件
可以修改代码将结果写入文件：
```java
// 在方法最后添加
Files.write(Paths.get("coverage-report.txt"), 
           reportContent.toString().getBytes());
```

## 📞 支持

如果遇到问题：

1. 检查 `application.properties` 配置
2. 确认Git仓库状态正常
3. 验证文件路径格式正确
4. 查看测试日志获取详细错误信息

---

**💡 提示**: 这个工具主要用于快速诊断和验证，对于大规模的覆盖率分析，建议使用完整的API接口。
