# 无阈值覆盖率计算算法说明

## 概述

本文档说明了CoverageEvaluator中实现的无阈值覆盖率计算算法，该算法采用方案2的设计理念，实现了更精确、更合理的覆盖率计算。

## 核心设计理念

### 方案2：所有块都加入matchedBlocks，移除阈值判断

**核心思想**：
- 所有块都根据相似度贡献覆盖率，不再使用硬阈值划分
- 阈值仅用于展示层的UI渲染，不影响实际的覆盖率计算
- 每个块的贡献 = 相似度 × 行数权重

## 算法实现

### 1. 基础覆盖率计算（evaluateFile方法）

```java
public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile) {
    List<DiffBlock> originBlocks = safeBlocks(originFile);
    List<DiffBlock> targetBlocks = safeBlocks(targetFile);

    int totalLines = 0;
    double matchedWeightedLines = 0D;
    List<DiffBlock> matchedBlocks = new ArrayList<>();

    for (DiffBlock originBlock : originBlocks) {
        int lineCount = estimateLineCount(originBlock);
        totalLines += lineCount;

        double similarity = findBestSimilarity(originBlock, targetBlocks);
        matchedWeightedLines += similarity * lineCount;
        
        // 方案2：所有块都加入matchedBlocks，移除阈值判断
        matchedBlocks.add(originBlock);
    }

    // 防止除以零：若 ΔO 无有效行，则视为完全覆盖
    double coverage = totalLines == 0 ? 1D : matchedWeightedLines / (double) totalLines;
    
    return CoverageDetail.builder()
            .filePath(resolveFilePath(originFile, targetFile))
            .coverage(coverage)
            .matchedLines(matchedWeightedLines)
            .totalLines(totalLines)
            .matchedBlocks(matchedBlocks)
            .unmatchedBlocks(Collections.emptyList()) // 无阈值版本，未匹配列表为空
            .build();
}
```

### 2. 带阈值展示的覆盖率计算（向后兼容）

```java
public CoverageDetail evaluateFile(DiffFile originFile, DiffFile targetFile, double threshold) {
    // 先用无阈值算法计算覆盖率
    CoverageDetail baseDetail = evaluateFile(originFile, targetFile);
    
    // 然后根据阈值重新划分块列表（仅用于展示）
    List<DiffBlock> highSimilarityBlocks = new ArrayList<>();
    List<DiffBlock> lowSimilarityBlocks = new ArrayList<>();
    
    // 为每个块重新计算相似度以进行划分
    List<DiffBlock> targetBlocks = safeBlocks(targetFile);
    for (DiffBlock originBlock : baseDetail.getMatchedBlocks()) {
        double similarity = findBestSimilarity(originBlock, targetBlocks);
        if (similarity >= threshold) {
            highSimilarityBlocks.add(originBlock);
        } else {
            lowSimilarityBlocks.add(originBlock);
        }
    }
    
    // 返回相同覆盖率但重新划分的块列表
    return CoverageDetail.builder()
            .filePath(baseDetail.getFilePath())
            .coverage(baseDetail.getCoverage())
            .matchedLines(baseDetail.getMatchedLines())
            .totalLines(baseDetail.getTotalLines())
            .matchedBlocks(highSimilarityBlocks)  // 高相似度块
            .unmatchedBlocks(lowSimilarityBlocks) // 低相似度块
            .build();
}
```

### 3. 基于映射的覆盖率计算（evaluateFileWithMapping）

```java
public CoverageDetail evaluateFileWithMapping(DiffFile originFile, DiffFile targetFile, double threshold) {
    List<DiffBlock> originBlocks = safeBlocks(originFile);
    List<DiffBlock> targetBlocks = safeBlocks(targetFile);

    // 使用新的块映射算法建立一一对应关系
    BlockMapping mapping = blockMapper.createMapping(originBlocks, targetBlocks);

    // 计算总行数和匹配行数
    int totalLines = 0;
    double matchedWeightedLines = 0D;
    List<DiffBlock> allBlocks = new ArrayList<>();

    // 处理已匹配的块：使用映射中的相似度
    for (DiffBlock originBlock : mapping.getMatchedOracleBlocks()) {
        int lineCount = estimateLineCount(originBlock);
        totalLines += lineCount;

        double similarity = mapping.getSimilarity(originBlock);
        matchedWeightedLines += similarity * lineCount;
        allBlocks.add(originBlock);  // 直接加入，无需阈值判断
    }

    // 处理未匹配的块：相似度为0，但仍计入总数
    for (DiffBlock originBlock : mapping.getUnmatchedOracle()) {
        int lineCount = estimateLineCount(originBlock);
        totalLines += lineCount;
        allBlocks.add(originBlock);  // 加入allBlocks，但贡献为0
    }

    // 防止除以零：若 ΔO 无有效行，则视为完全覆盖
    double coverage = totalLines == 0 ? 1D : matchedWeightedLines / (double) totalLines;
    
    // 根据阈值重新划分块列表（仅用于展示）
    List<DiffBlock> highSimilarityBlocks = new ArrayList<>();
    List<DiffBlock> lowSimilarityBlocks = new ArrayList<>();
    
    for (DiffBlock originBlock : mapping.getMatchedOracleBlocks()) {
        double similarity = mapping.getSimilarity(originBlock);
        if (similarity >= threshold) {
            highSimilarityBlocks.add(originBlock);
        } else {
            lowSimilarityBlocks.add(originBlock);
        }
    }
    
    // 未匹配的块都加入低相似度列表
    lowSimilarityBlocks.addAll(mapping.getUnmatchedOracle());
    
    return CoverageDetail.builder()
            .filePath(resolveFilePath(originFile, targetFile))
            .coverage(coverage)
            .matchedLines(matchedWeightedLines)
            .totalLines(totalLines)
            .matchedBlocks(highSimilarityBlocks)  // 高相似度块
            .unmatchedBlocks(lowSimilarityBlocks) // 低相似度块
            .build();
}
```

## 4-Way相似度计算

### 核心算法

```java
private double blockSimilarity(DiffBlock origin, DiffBlock target) {
    // --- Normalize content ---
    String oFromStr = normalize(origin.getContentFrom());
    String oToStr   = normalize(origin.getContentTo());
    String tFromStr = normalize(target.getContentFrom());
    String tToStr   = normalize(target.getContentTo());

    // --- Tokenize once (性能优化的关键部分) ---
    List<String> oFrom = tokenizeIfNotEmpty(oFromStr);
    List<String> oTo   = tokenizeIfNotEmpty(oToStr);
    List<String> tFrom = tokenizeIfNotEmpty(tFromStr);
    List<String> tTo   = tokenizeIfNotEmpty(tToStr);

    // --- Weighted 4-way similarity ---
    double deleteSimilarity = score(oFrom, tFrom, 1.0);  // 删除同源
    double addSimilarity = score(oTo, tTo, 1.0);    // 添加同源
    double crossSimilarity1 = score(oFrom, tTo, 0.3); // cross
    double crossSimilarity2 = score(oTo, tFrom, 0.3); // cross
    
    // 返回最大值，优先同源匹配
    return Math.max(Math.max(deleteSimilarity, addSimilarity), 
                 Math.max(crossSimilarity1, crossSimilarity2));
}
```

### 权重设计

- **deleteSimilarity**: 权重1.0 - 删除操作的源码匹配
- **addSimilarity**: 权重1.0 - 添加操作的源码匹配  
- **crossSimilarity1**: 权重0.3 - 跨匹配（O的删除 vs G的添加）
- **crossSimilarity2**: 权重0.3 - 跨匹配（O的添加 vs G的删除）

**设计原理**：
1. 同源匹配（delete/add）具有最高优先级，权重为1.0
2. 跨匹配作为补充，权重为0.3，避免误匹配
3. 取最大值确保选择最佳匹配路径

## 性能优化

### 1. Token预计算

```java
private List<String> tokenizeIfNotEmpty(String s) {
    if (s == null || s.isEmpty()) return null;
    return CoverageUtils.tokenize(s, enableNoiseFiltering);
}
```

- 每个字符串只进行一次tokenization
- 空字符串直接返回null，避免无谓计算
- 支持噪音过滤（可选）

### 2. 智能缓存（在OrderAwareBlockMapper中）

- 缓存相似度计算结果
- LRU策略避免内存泄漏
- 基于内容哈希的缓存键

## 算法优势

### 1. 精确性提升

- **传统算法**：0/1二分法，要么完全匹配，要么完全不匹配
- **新算法**：0-1连续值，根据实际相似度贡献覆盖率

### 2. 公平性

- 小幅修改的块不会被完全忽略
- 大幅修改的块仍有部分贡献
- 避免阈值附近的"悬崖效应"

### 3. 可解释性

- 每个块的贡献清晰可见
- 覆盖率 = Σ(相似度 × 行数) / 总行数
- 支持细粒度的分析

## 兼容性

### 1. API兼容

- 保留原有的`evaluateFile(originFile, targetFile, threshold)`方法
- 新增无阈值版本`evaluateFile(originFile, targetFile)`
- 返回的CoverageDetail结构保持一致

### 2. 前端兼容

- 高/低相似度块的划分逻辑保持不变
- 展示层阈值仍可配置
- 新增BlockMappingViewer展示详细映射关系

## 测试验证

### 1. 基础功能测试

- 完全匹配：覆盖率 = 1.0
- 部分匹配：覆盖率 = 相似度
- 无匹配：覆盖率 = 0.0

### 2. 边界情况测试

- 空文件：覆盖率 = 1.0（视为完全覆盖）
- 空内容块：行数 = 0，不影响总覆盖率
- 混合相似度：加权平均正确计算

### 3. 性能测试

- 大文件处理：线性时间复杂度
- 内存使用：可控的缓存大小
- 并发安全：线程安全设计

## 配置选项

### 1. 噪音过滤

```properties
coverage.filter.noise.enabled=false
```

- 是否过滤import语句和注释
- 默认关闭，保持算法通用性

### 2. 展示阈值

```java
private static final double DISPLAY_SIMILARITY_THRESHOLD = 0.85D;
```

- 用于前端UI的高亮显示
- 不影响实际的覆盖率计算

## 使用示例

### 1. 基础用法

```java
CoverageEvaluator evaluator = new CoverageEvaluator();

// 无阈值计算（推荐）
CoverageDetail detail = evaluator.evaluateFile(oracleFile, gaussFile);

// 带阈值展示（兼容性）
CoverageDetail detail = evaluator.evaluateFile(oracleFile, gaussFile, 0.85);
```

### 2. 基于映射的计算

```java
// 使用OrderAwareBlockMapper进行精确映射
CoverageDetail detail = evaluator.evaluateFileWithMapping(oracleFile, gaussFile, 0.85);

// 获取映射关系用于迁移功能
BlockMapping mapping = evaluator.getBlockMapping(oracleFile, gaussFile);
```

## 总结

无阈值覆盖率计算算法通过以下改进实现了更精确的覆盖率评估：

1. **连续化**：从0/1二分法改为0-1连续值
2. **公平性**：每个块都按实际相似度贡献
3. **精确性**：基于4-way相似度的精确计算
4. **性能**：Token预计算和智能缓存
5. **兼容性**：保持API和前端兼容

该算法为代码迁移和质量评估提供了更准确、更可靠的覆盖率指标。
