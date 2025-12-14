# 块映射算法详细说明

## 🤔 核心问题回答

### Q1: 如果ΔO块A匹配了ΔG块B，那么B将不能被任何块匹配吗？

**答案：是的！** 这是当前算法的核心设计原则。

**具体机制**：
```java
Set<DiffBlock> usedGBlocks = new HashSet<>();

// 在findBestMatchInWindow方法中：
if (usedGBlocks.contains(gBlock)) {
    continue; // 跳过已使用的块
}
```

**设计理念**：
- ✅ **一一对应原则**：每个ΔG块最多只能被一个ΔO块匹配
- ✅ **避免重复计算**：防止一个ΔG块的相似度被多次计入覆盖率
- ✅ **结果确定性**：相同输入始终产生相同的映射结果

**实际影响**：
- 如果ΔO₁找到了最佳匹配ΔG₁，那么ΔO₂、ΔO₃...将不能再匹配ΔG₁
- 这确保了覆盖率的准确性，避免了"一个块抵消多个块"的情况

---

### Q2: 阈值设置多少？

**当前阈值设置**：
```java
/** 最小相似度阈值 */
private static final double MIN_SIMILARITY = 0.6;  // 60%
```

**阈值含义**：
- 只有当相似度 ≥ 60% 时，才会建立匹配关系
- 如果最高相似度 < 60%，该ΔO块将被标记为"未匹配"

**为什么选择60%？**
- 🎯 **平衡精度和召回率**：避免过低相似度导致的误匹配，也避免过高阈值导致的漏匹配
- 📊 **实践经验**：基于代码相似度的统计分析，60%是较好的平衡点
- 🔧 **可配置性**：如果需要调整，可以修改这个常量

---

## 🔄 算法执行流程

### 1. 位置窗口搜索
```java
// 位置搜索窗口大小
private static final int POSITION_WINDOW = 3;

// 计算搜索窗口
int windowStart = Math.max(0, oIndex - POSITION_WINDOW);
int windowEnd = Math.min(gBlocks.size() - 1, oIndex + POSITION_WINDOW);
```

**含义**：
- ΔOᵢ 只能在 ΔGᵢ₋₃ 到 ΔGᵢ₊₃ 的范围内寻找匹配
- 这样既考虑了位置相近性，又允许一定的位置偏移

### 2. 最佳匹配选择
```java
for (int gIndex = windowStart; gIndex <= windowEnd; gIndex++) {
    DiffBlock gBlock = gBlocks.get(gIndex);
    
    if (usedGBlocks.contains(gBlock)) {
        continue; // 已被其他块占用，跳过
    }
    
    double similarity = calculateSimilarity(oBlock, gBlock);
    if (similarity > bestSimilarity) {
        bestSimilarity = similarity;
        bestMatch = gBlock;
    }
}
```

**选择策略**：
- 在位置窗口内寻找**相似度最高**的未使用ΔG块
- 如果找到的块相似度 ≥ 60%，则建立匹配
- 否则该ΔO块标记为未匹配

### 3. 4路相似度计算
```java
return max(
    score(oFrom, gFrom, 1.0),  // 删除同源 - 权重100%
    score(oTo, gTo, 1.0),      // 添加同源 - 权重100%
    score(oFrom, gTo, 0.3),      // 交叉匹配 - 权重30%
    score(oTo, gFrom, 0.3)       // 交叉匹配 - 权重30%
);
```

**计算方式**：
- 主要比较相同类型的变更（删除vs删除，添加vs添加）
- 适当考虑交叉匹配（删除vs添加）
- 使用`CoverageUtils.recallSimilarity`计算token级别的相似度

---

## 📊 实际示例

### 示例场景
假设有以下块序列：
```
ΔO: [O₁, O₂, O₃, O₄]
ΔG: [G₁, G₂, G₃, G₄, G₅]
```

### 匹配过程
1. **处理O₁**：
   - 在位置窗口 [G₀-G₄] 中搜索
   - 找到最佳匹配G₂，相似度85% ≥ 60%
   - 建立匹配：O₁ ↔ G₂
   - 标记G₂为已使用

2. **处理O₂**：
   - 在位置窗口 [G₀-G₅] 中搜索
   - G₂已被使用，跳过
   - 找到最佳匹配G₃，相似度72% ≥ 60%
   - 建立匹配：O₂ ↔ G₃
   - 标记G₃为已使用

3. **处理O₃**：
   - 在位置窗口 [G₀-G₆] 中搜索
   - G₂、G₃已被使用，跳过
   - 找到最佳匹配G₁，相似度45% < 60%
   - 不建立匹配，O₃标记为未匹配

4. **处理O₄**：
   - 在位置窗口 [G₁-G₇] 中搜索
   - 找到最佳匹配G₅，相似度78% ≥ 60%
   - 建立匹配：O₄ ↔ G₅
   - 标记G₅为已使用

5. **最终结果**：
   ```
   匹配：O₁↔G₂(85%), O₂↔G₃(72%), O₄↔G₅(78%)
   未匹配ΔO：O₃
   未匹配ΔG：G₁, G₄
   ```

---

## ⚙️ 配置参数说明

### 当前可调参数
```java
// 位置窗口大小 - 控制搜索范围
private static final int POSITION_WINDOW = 3;

// 最小相似度阈值 - 控制匹配严格程度
private static final double MIN_SIMILARITY = 0.6;

// 噪音过滤开关 - 过滤import和注释
@Value("${coverage.filter.noise.enabled:false}")
private boolean enableNoiseFiltering = false;
```

### 参数影响分析

| 参数 | 增大值的影响 | 减小值的影响 | 推荐值 |
|------|---------------|---------------|----------|
| POSITION_WINDOW | 匹配更灵活，可能跨更远位置 | 匹配更严格，要求位置更接近 | 3-5 |
| MIN_SIMILARITY | 更多匹配，可能降低质量 | 更少匹配，提高匹配质量 | 0.5-0.7 |
| enableNoiseFiltering | 过滤噪音，提高语义匹配 | 保留所有内容，可能噪音干扰 | true |

---

## 🔧 自定义配置

### 方法1：修改常量值
```java
// 在OrderAwareBlockMapper.java中修改
private static final double MIN_SIMILARITY = 0.7;  // 提高到70%
private static final int POSITION_WINDOW = 5;      // 扩大到5
```

### 方法2：使用配置文件
在`application.properties`中添加：
```properties
coverage.filter.noise.enabled=true
# 未来可以扩展其他参数的配置化
```

### 方法3：动态配置（推荐扩展）
```java
@Component
@ConfigurationProperties(prefix = "block.mapping")
public class BlockMappingConfig {
    private int positionWindow = 3;
    private double minSimilarity = 0.6;
    private boolean enableNoiseFiltering = false;
    
    // getters and setters
}
```

---

## 📈 算法优化建议

### 1. 自适应阈值
```java
// 根据整体相似度分布动态调整阈值
double adaptiveThreshold = calculateAdaptiveThreshold(allSimilarities);
```

### 2. 多轮匹配
```java
// 第一轮：严格匹配（高阈值）
// 第二轮：宽松匹配（低阈值，仅处理未匹配块）
```

### 3. 上下文感知
```java
// 考虑前后块的内容和匹配情况
double contextScore = calculateContextScore(oBlock, gBlock, previousMatches);
```

---

## 🎯 总结

**当前算法特点**：
- ✅ **一一对应**：确保映射的唯一性和准确性
- ✅ **位置感知**：考虑文件顺序，提高匹配合理性
- ✅ **阈值控制**：60%相似度阈值，平衡精度和召回率
- ✅ **噪音过滤**：可选择过滤import语句和注释

**适用场景**：
- 代码迁移和同步分析
- 版本间差异对比
- 代码覆盖率计算
- 变更影响分析

如果需要调整匹配行为，主要修改`MIN_SIMILARITY`和`POSITION_WINDOW`这两个参数即可。
