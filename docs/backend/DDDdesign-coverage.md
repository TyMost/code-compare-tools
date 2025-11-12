整理版如下（可直接保存为后端技术设计文档或交给 Codex 生成代码）：

---

# 🧱 增量迁移对比平台后端设计文档（含迁移覆盖度模块）

---

## 📁 一、整体目录结构

```
com.example.migratediff/
├── api/                              # 接口层（Controller + DTO + Mapper）
│   ├── controller/
│   │   ├── DiffController.java
│   │   ├── MigrationController.java
│   │   ├── CoverageController.java        # 【🆕 新增】迁移覆盖度接口
│   │   ├── RepoController.java
│   │   └── SettingController.java
│   ├── dto/
│   │   ├── DiffRequestDTO.java
│   │   ├── DiffResponseDTO.java
│   │   ├── MigrationRequestDTO.java
│   │   ├── MigrationResponseDTO.java
│   │   ├── CoverageRequestDTO.java        # 【🆕 新增】
│   │   └── CoverageResponseDTO.java       # 【🆕 新增】
│   └── mapper/
│       ├── DiffMapper.java
│       ├── MigrationMapper.java
│       └── CoverageMapper.java            # 【🆕 新增】
│
├── application/                      # 应用服务层
│   ├── DiffAppService.java
│   ├── MigrationAppService.java
│   ├── CoverageAppService.java            # 【🆕 新增】
│   ├── RepoAppService.java
│   ├── SettingAppService.java
│   └── GenerateAppService.java
│
├── domain/                           # 领域模型层（核心）
│   ├── diff/
│   │   ├── DiffFile.java
│   │   ├── DiffBlock.java
│   │   ├── DiffType.java
│   │   ├── DiffSummary.java
│   │   ├── DeltaGroup.java
│   │   └── DeltaType.java
│   │
│   ├── coverage/                          # 【🆕 新增】迁移覆盖率领域模型
│   │   ├── CoverageMetric.java
│   │   ├── CoverageDetail.java
│   │   ├── CoverageSummary.java
│   │   └── CoverageEvaluator.java
│   │
│   ├── migration/
│   │   ├── MigrationTask.java
│   │   ├── MigrationResult.java
│   │   ├── MigrationStatus.java
│   │   ├── MigrationMapping.java
│   │   └── MigrationSummary.java
│   │
│   ├── repo/
│   │   ├── RepoConfig.java
│   │   ├── RepoBranch.java
│   │   ├── RepoPath.java
│   │   └── RepoType.java
│   │
│   ├── setting/
│   │   └── SystemSetting.java
│   │
│   └── common/
│       ├── FilePath.java
│       ├── CodeFragment.java
│       └── Range.java
│
├── infrastructure/
│   ├── git/
│   │   ├── GitDiffAdapter.java
│   │   ├── GitRepoScanner.java
│   │   ├── GitBranchFetcher.java
│   │   └── GitDiffParser.java
│   │
│   ├── persistence/
│   │   ├── DiffRepository.java
│   │   ├── MigrationRepository.java
│   │   ├── CoverageRepository.java        # 【🆕 新增】
│   │   ├── RepoRepository.java
│   │   └── SettingRepository.java
│   │
│   ├── mapper/
│   │   ├── DiffMapper.java
│   │   ├── MigrationMapper.java
│   │   ├── CoverageMapper.java            # 【🆕 新增】
│   │   └── RepoMapper.java
│   │
│   └── config/
│       └── AppConfig.java
│
├── shared/
│   ├── exception/
│   │   ├── BusinessException.java
│   │   └── NotFoundException.java
│   ├── utils/
│   │   ├── JsonUtils.java
│   │   ├── PathUtils.java
│   │   └── CoverageUtils.java             # 【🆕 新增】提供相似度计算
│   └── constants/
│       └── AppConstants.java
│
└── log/
    └── OperationLog.java
```

---

## 🧩 二、每层职责与设计逻辑

### 1️⃣ `api` 层 — 系统入口与数据暴露

- **职责**：接收 HTTP 请求、执行参数校验、完成 DTO 与领域模型的转换，调用应用层服务。
- **亮点**：新增 `CoverageController` 及相关 DTO/Mapper，提供迁移覆盖度分析接口。

### 2️⃣ `application` 层 — 业务编排与事务边界

- **职责**：编排跨领域的业务流程，维护事务边界，处理异常与日志。
- **亮点**：新增 `CoverageAppService`，在迁移完成后触发覆盖度分析，形成可追踪指标。

### 3️⃣ `domain` 层 — 核心业务模型

- **职责**：承载 Diff、Migration、Coverage 等核心领域模型，独立于技术框架。
- **亮点**：迁移覆盖度模型拆分为 `CoverageDetail`、`CoverageSummary`、`CoverageMetric`、`CoverageEvaluator`，支持对 ΔO/ΔG 进行覆盖校验与评分。

### 4️⃣ `infrastructure` 层 — 技术实现细节

- **职责**：实现仓储、Git 适配、配置管理等技术细节。
- **亮点**：新增 `CoverageRepository`，持久化覆盖度结果；`CoverageMapper` 对接持久化/接口层。

### 5️⃣ `shared` 层 — 通用支撑能力

- **职责**：提供工具类、异常、常量等跨领域通用能力。
- **亮点**：新增 `CoverageUtils`，封装文本相似度算法（Jaccard、Levenshtein 等），支撑覆盖度计算。

---

## 🧠 三、迁移覆盖度模块设计

### 背景

现有系统可回答“迁移什么”和“如何迁移”，但缺乏“迁移是否完整、正确”的反馈。迁移覆盖度模块用于评估 ΔO 中的变更是否被准确迁入 ΔG，形成闭环。

### 设计目标

1. 判定 ΔO 中每个 `DiffBlock` 是否在 ΔG 中找到对应变更；
2. 输出覆盖率、相似度等量化指标，支撑质量预警；
3. 形成历史记录，支撑回溯分析与持续优化。

### 核心领域模型（`domain/coverage`）

```java
public class CoverageDetail {
    private String filePath;
    private int totalBlocks;                // ΔO 变更块数量
    private int coveredBlocks;              // 成功迁移的块数量
    private double coverageRate;            // 覆盖率 = covered / total
    private List<DiffBlock> uncoveredBlocks;// 未覆盖的增量块
}

public class CoverageSummary {
    private List<CoverageDetail> fileCoverages;
    private double totalCoverageRate;       // 全局覆盖率
    private int totalFiles;
    private int fullyCoveredFiles;          // 100% 覆盖的文件数
}

public class CoverageMetric {
    private String filePath;
    private double similarityScore;         // ΔO 与 ΔG 的相似度
    private boolean fullyCovered;           // 是否达到覆盖阈值
}
```

### 计算逻辑（`CoverageEvaluator`）

```java
public class CoverageEvaluator {

    public CoverageSummary evaluateCoverage(DeltaGroup group) {
        // 1. 遍历 deltaO 的 DiffBlock
        // 2. 计算与 deltaG 的相似度（文本或 AST）
        // 3. 统计覆盖数量与评分
        // 4. 组装 CoverageSummary 返回
    }
}
```

**实现要点**

- 复用 `CoverageUtils` 的相似度算法（Jaccard/Levenshtein）。
- `MigrationAppService` 在迁移执行后调用 `CoverageEvaluator`。
- 结果写入 `CoverageRepository`，支持对外查询与报表输出。

---

## 🧱 四、扩展位置速查

| 模块路径                          | 文件/组件                     | 扩展原因                         |
| ----------------------------- | ------------------------- | ---------------------------- |
| `domain/coverage/`            | CoverageDetail 等         | 封装覆盖度领域模型与算法             |
| `application/`                | CoverageAppService        | 对外暴露覆盖度分析能力               |
| `infrastructure/persistence/` | CoverageRepository        | 持久化覆盖度结果，便于历史追踪         |
| `api/controller/`             | CoverageController        | 提供覆盖度 REST 接口               |
| `api/mapper/`                 | CoverageMapper            | DTO ↔ Domain 映射                |
| `shared/utils/`               | CoverageUtils             | 相似度算法与工具集                 |
| `application/`                | MigrationAppService       | 迁移完成后触发覆盖度评估             |

---

## 📊 五、覆盖度响应样例（`CoverageResponseDTO`）

```json
{
  "totalCoverageRate": 0.85,
  "fullyCoveredFiles": 12,
  "totalFiles": 15,
  "fileCoverages": [
    {
      "filePath": "src/main/java/com/demo/UserService.java",
      "totalBlocks": 10,
      "coveredBlocks": 9,
      "coverageRate": 0.9,
      "uncoveredBlocks": [
        { "startLineFrom": 50, "endLineFrom": 55, "type": "MODIFY" }
      ]
    }
  ]
}
```

---

## ⚙️ 六、迁移流程与覆盖度结果

| 阶段 | 操作                     | 输出对象              | 说明                       |
| ---- | ------------------------ | --------------------- | -------------------------- |
| 1    | 比较 ΔO、ΔG               | `DeltaGroup`          | 提取 Oracle 与 Gauss 增量差异 |
| 2    | 执行迁移（应用 Mapping）   | `MigrationResult`     | 完成变更迁移与冲突处理           |
| 3    | 计算迁移覆盖度             | `CoverageSummary`     | 统计覆盖率、未覆盖块、相似度等指标  |
| 4    | 生成报告/可视化            | 前端展示               | 在 ΔO/ΔG 对比界面提示覆盖情况     |

---

## ✅ 七、核心收益

1. **数据闭环**：从“发现差异”到“验证迁移”形成完整链路；
2. **量化度量**：覆盖度指标可设置阈值，支持自动预警；
3. **模块解耦**：覆盖度逻辑独立于迁移逻辑，方便演进；
4. **持续优化**：可迭代引入 AST、AI 对比等高级算法。

---

## 🧱 八、迁移覆盖度模块骨架代码

### domain/coverage

```java
package com.example.migratediff.domain.coverage;

import java.util.List;
import com.example.migratediff.domain.diff.DiffBlock;

public class CoverageDetail {
    private final String filePath;
    private final int totalBlocks;
    private final int coveredBlocks;
    private final double coverageRate;
    private final List<DiffBlock> uncoveredBlocks;

    public CoverageDetail(String filePath,
                          int totalBlocks,
                          int coveredBlocks,
                          double coverageRate,
                          List<DiffBlock> uncoveredBlocks) {
        // TODO: 参数校验（覆盖率范围、块数量一致性等）
        this.filePath = filePath;
        this.totalBlocks = totalBlocks;
        this.coveredBlocks = coveredBlocks;
        this.coverageRate = coverageRate;
        this.uncoveredBlocks = uncoveredBlocks;
    }

    // TODO: getter 与业务方法
}
```

```java
package com.example.migratediff.domain.coverage;

import java.util.List;

public class CoverageSummary {
    private final List<CoverageDetail> fileCoverages;
    private final double totalCoverageRate;
    private final int totalFiles;
    private final int fullyCoveredFiles;

    public CoverageSummary(List<CoverageDetail> fileCoverages,
                           double totalCoverageRate,
                           int totalFiles,
                           int fullyCoveredFiles) {
        // TODO: 参数校验与聚合逻辑
        this.fileCoverages = fileCoverages;
        this.totalCoverageRate = totalCoverageRate;
        this.totalFiles = totalFiles;
        this.fullyCoveredFiles = fullyCoveredFiles;
    }

    // TODO: getter 与聚合辅助方法
}
```

```java
package com.example.migratediff.domain.coverage;

public class CoverageMetric {
    private final String filePath;
    private final double similarityScore;
    private final boolean fullyCovered;

    public CoverageMetric(String filePath, double similarityScore, boolean fullyCovered) {
        // TODO: 校验相似度区间并设置阈值
        this.filePath = filePath;
        this.similarityScore = similarityScore;
        this.fullyCovered = fullyCovered;
    }

    // TODO: getter 与阈值判断
}
```

```java
package com.example.migratediff.domain.coverage;

import com.example.migratediff.domain.diff.DeltaGroup;

public class CoverageEvaluator {

    public CoverageSummary evaluateCoverage(DeltaGroup group) {
        // TODO: 1) 遍历 deltaO 块；2) 匹配 deltaG；3) 生成 CoverageSummary
        return null;
    }
}
```

### application

```java
package com.example.migratediff.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.migratediff.domain.coverage.CoverageEvaluator;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DeltaGroup;
import com.example.migratediff.infrastructure.persistence.CoverageRepository;

@Service
public class CoverageAppService {

    private final CoverageEvaluator evaluator;
    private final CoverageRepository repository;

    public CoverageAppService(CoverageEvaluator evaluator, CoverageRepository repository) {
        this.evaluator = evaluator;
        this.repository = repository;
    }

    @Transactional
    public CoverageSummary analyzeCoverage(DeltaGroup group) {
        CoverageSummary summary = evaluator.evaluateCoverage(group);
        repository.save(summary);
        return summary;
    }
}
```

```java
package com.example.migratediff.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.migratediff.domain.diff.DeltaGroup;

@Service
public class MigrationAppService {

    private final CoverageAppService coverageAppService;

    public MigrationAppService(CoverageAppService coverageAppService) {
        this.coverageAppService = coverageAppService;
    }

    @Transactional
    public void migrate(DeltaGroup group) {
        // TODO: 执行迁移主流程（差异合并、冲突处理、入库等）
        coverageAppService.analyzeCoverage(group);
    }
}
```

### api

```java
package com.example.migratediff.api.dto;

public class CoverageRequestDTO {
    // TODO: 任务标识、Delta 数据、覆盖阈值等请求字段
}
```

```java
package com.example.migratediff.api.dto;

public class CoverageResponseDTO {
    // TODO: 对应 CoverageSummary 的响应字段
}
```

```java
package com.example.migratediff.api.mapper;

import com.example.migratediff.api.dto.CoverageRequestDTO;
import com.example.migratediff.api.dto.CoverageResponseDTO;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DeltaGroup;

public final class CoverageMapper {

    private CoverageMapper() {
    }

    public static DeltaGroup toDomain(CoverageRequestDTO dto) {
        // TODO: DTO → DeltaGroup 转换
        return null;
    }

    public static CoverageResponseDTO toResponse(CoverageSummary summary) {
        // TODO: CoverageSummary → DTO
        return null;
    }
}
```

```java
package com.example.migratediff.api.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.migratediff.api.dto.CoverageRequestDTO;
import com.example.migratediff.api.dto.CoverageResponseDTO;
import com.example.migratediff.api.mapper.CoverageMapper;
import com.example.migratediff.application.CoverageAppService;
import com.example.migratediff.domain.coverage.CoverageSummary;
import com.example.migratediff.domain.diff.DeltaGroup;

@RestController
@RequestMapping("/coverage")
public class CoverageController {

    private final CoverageAppService coverageAppService;

    public CoverageController(CoverageAppService coverageAppService) {
        this.coverageAppService = coverageAppService;
    }

    @PostMapping("/analyze")
    public CoverageResponseDTO analyze(@RequestBody CoverageRequestDTO dto) {
        DeltaGroup group = CoverageMapper.toDomain(dto);
        CoverageSummary summary = coverageAppService.analyzeCoverage(group);
        return CoverageMapper.toResponse(summary);
    }
}
```

### infrastructure

```java
package com.example.migratediff.infrastructure.persistence;

import java.util.Optional;
import com.example.migratediff.domain.coverage.CoverageSummary;

public interface CoverageRepository {

    void save(CoverageSummary summary);

    Optional<CoverageSummary> findByTaskId(String taskId);
}
```

### shared

```java
package com.example.migratediff.shared.utils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class CoverageUtils {

    private CoverageUtils() {
    }

    public static double jaccardSimilarity(List<String> sourceTokens, List<String> targetTokens) {
        Set<String> sourceSet = Set.copyOf(sourceTokens);
        Set<String> targetSet = Set.copyOf(targetTokens);
        long intersection = sourceSet.stream().filter(targetSet::contains).count();
        long union = sourceSet.size() + targetSet.size() - intersection;
        return union == 0 ? 1D : (double) intersection / union;
    }

    public static int levenshteinDistance(String source, String target) {
        // TODO: 实现编辑距离计算
        return 0;
    }
}
```

---

## 💡 九、Codex 提示词

> 以下提示词可直接给 Codex，用于生成覆盖度模块代码（包含领域、应用、接口三层骨架）。

```
codex> 请在项目 com.example.migratediff 中新增一个“迁移覆盖度”模块。
要求：
1. 在 domain/coverage 下创建 CoverageDetail、CoverageSummary、CoverageMetric、CoverageEvaluator；
2. 在 application 层新增 CoverageAppService，并在 MigrationAppService 内调用；
3. 在 api 层新增 CoverageController、CoverageRequestDTO、CoverageResponseDTO、CoverageMapper；
4. 在 persistence 层新增 CoverageRepository；
5. 在 shared/utils 下增加 CoverageUtils，提供文本相似度计算（Jaccard/Levenshtein）；
6. 所有类遵循既有包命名与 DDD 分层结构。
```
