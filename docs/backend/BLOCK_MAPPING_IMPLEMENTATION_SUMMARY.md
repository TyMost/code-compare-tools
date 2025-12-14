# BlockMapping实现总结

## 问题背景

你提到当前的git差异块在o和g完全没有联系，使用的是取几个块之间计算的最高覆盖率。这个问题导致了覆盖率计算不够精确，无法建立ΔO和ΔG之间的一一对应关系。

## 解决方案

我们实现了一个基于顺序一致性的块映射系统，包含以下核心组件：

### 1. BlockMapping核心数据结构

**文件**: `backend/src/main/java/com/example/migratediff/domain/coverage/BlockMapping.java`

**功能**:
- 存储ΔO和ΔG块之间的映射关系
- 维护匹配对、未匹配块和相似度信息
- 提供覆盖率计算和查询接口

**核心方法**:
```java
// 添加匹配关系
public void addMatch(DiffBlock oracleBlock, DiffBlock gaussBlock, double similarity)

// 获取覆盖率
public double getCoverage()

// 获取映射关系
public DiffBlock getGaussBlock(DiffBlock oracleBlock)
public Double getSimilarity(DiffBlock oracleBlock)
```

### 2. OrderAwareBlockMapper算法

**文件**: `backend/src/main/java/com/example/migratediff/domain/coverage/OrderAwareBlockMapper.java`

**核心特性**:
- **位置窗口搜索**: 在±3行范围内寻找匹配块
- **一一映射**: 确保每个ΔO块最多匹配一个ΔG块
- **噪音过滤**: 可配置的import语句和注释过滤
- **4路相似度计算**: 与原有CoverageEvaluator保持一致

**算法流程**:
1. 按顺序遍历ΔO块
2. 在位置窗口内寻找最佳匹配的ΔG块
3. 应用相似度阈值过滤(≥0.6)
4. 建立一一映射关系
5. 标记未匹配的块

**配置参数**:
```java
private static final int POSITION_WINDOW = 3;      // 位置窗口大小
private static final double MIN_SIMILARITY = 0.6;  // 最小相似度阈值
```

### 3. CoverageEvaluator集成

**文件**: `backend/src/main/java/com/example/migratediff/domain/coverage/CoverageEvaluator.java`

**新增方法**:
```java
// 基于映射的覆盖率计算
public CoverageDetail evaluateFileWithMapping(DiffFile originFile, DiffFile targetFile, double threshold)

// 获取块映射关系
public BlockMapping getBlockMapping(DiffFile originFile, DiffFile targetFile)
```

**向后兼容**:
- 保留原有的`evaluateFile`方法
- 新方法提供更精确的基于映射的计算
- Spring注入OrderAwareBlockMapper依赖

### 4. 噪音过滤支持

**配置**: `backend/src/main/resources/application.properties`
```properties
# 启用噪音过滤（过滤import语句和注释）
coverage.filter.noise.enabled=true
```

**效果**:
- 过滤import语句差异
- 过滤注释差异
- 提高核心代码匹配精度

## 技术特点

### 1. 位置感知
- 考虑块在文件中的位置信息
- 优先匹配相近位置的块
- 支持小范围的代码重排

### 2. 一一映射
- 避免多对多匹配的混乱
- 清晰的对应关系
- 便于迁移功能使用

### 3. 性能优化
- 窗口搜索减少计算量
- 早期过滤无效块
- 4路相似度计算复用现有逻辑

### 4. 可配置性
- 位置窗口大小可调
- 相似度阈值可配置
- 噪音过滤可开关

## 使用示例

### 基本使用
```java
@Autowired
private CoverageEvaluator coverageEvaluator;

// 获取块映射
BlockMapping mapping = coverageEvaluator.getBlockMapping(oracleFile, gaussFile);

// 使用映射计算覆盖率
CoverageDetail detail = coverageEvaluator.evaluateFileWithMapping(
    oracleFile, gaussFile, 0.85
);
```

### 查询映射关系
```java
// 检查特定块的匹配情况
DiffBlock matchedGaussBlock = mapping.getGaussBlock(oracleBlock);
Double similarity = mapping.getSimilarity(oracleBlock);

// 获取统计信息
double coverage = mapping.getCoverage();
int matchedCount = mapping.getMatchedCount();
```

## 解决的问题

1. **一一对应关系**: 建立了明确的ΔO→ΔG映射
2. **位置一致性**: 考虑代码块的位置关系
3. **精确覆盖率**: 基于映射而非最高相似度计算
4. **噪音容忍**: 过滤非核心代码差异

## 后续扩展

1. **迁移功能集成**: 可用于代码迁移的块级映射
2. **可视化支持**: 前端可显示块对应关系
3. **性能调优**: 根据实际使用调整窗口大小和阈值
4. **更多过滤规则**: 可扩展其他噪音类型

## 文件清单

### 核心实现
- `BlockMapping.java` - 映射数据结构
- `OrderAwareBlockMapper.java` - 映射算法
- `CoverageEvaluator.java` - 集成和接口

### 测试文件
- `OrderAwareBlockMapperTest.java` - 单元测试
- `CoverageEvaluatorMappingTest.java` - 集成测试
- `BlockMappingIntegrationTest.java` - 简单集成测试

### 配置
- `application.properties` - 噪音过滤配置

这个实现为你的git差异块匹配问题提供了一个完整的解决方案，不仅解决了当前的覆盖率计算问题，还为未来的功能扩展奠定了基础。
