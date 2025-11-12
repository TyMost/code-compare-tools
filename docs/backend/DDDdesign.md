
--
# 🧱 增量迁移对比平台后端设计文档（Java / Spring Boot）

---

## 📁 一、整体目录结构

```
com.example.migratediff/
├── api/                              # 接口层（Controller + DTO + Mapper）
│   ├── controller/
│   │   ├── DiffController.java
│   │   ├── MigrationController.java
│   │   ├── RepoController.java
│   │   └── SettingController.java
│   ├── dto/
│   │   ├── DiffRequestDTO.java
│   │   ├── DiffResponseDTO.java
│   │   ├── MigrationRequestDTO.java
│   │   └── MigrationResponseDTO.java
│   └── mapper/
│       ├── DiffMapper.java
│       └── MigrationMapper.java
│
├── application/                      # 应用服务层
│   ├── DiffAppService.java
│   ├── MigrationAppService.java
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
│   ├── repo/
│   │   ├── RepoConfig.java
│   │   ├── RepoBranch.java
│   │   ├── RepoPath.java
│   │   └── RepoType.java
│   │
│   ├── migration/
│   │   ├── MigrationTask.java
│   │   ├── MigrationResult.java
│   │   ├── MigrationStatus.java
│   │   ├── MigrationMapping.java
│   │   └── MigrationSummary.java
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
│   │   ├── RepoRepository.java
│   │   └── SettingRepository.java
│   │
│   ├── mapper/
│   │   ├── DiffMapper.java
│   │   ├── MigrationMapper.java
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
│   │   └── PathUtils.java
│   └── constants/
│       └── AppConstants.java
│
└── log/
    └── OperationLog.java
```

---

## 🧩 二、每层职责与设计逻辑

### 1️⃣ `api` 层 — 系统入口与数据暴露

**职责**

* 接收 HTTP 请求，返回 JSON。
* 调用应用层服务。
* 做参数验证与数据映射。

**内容**

* `controller`: `DiffController`, `MigrationController`。
* `dto`: 请求与响应对象。
* `mapper`: DTO ↔ Domain 对象转换。

**好处**

* 接口层干净，易于联调。
* 保持前后端契约稳定。

---

### 2️⃣ `application` 层 — 业务编排与事务边界

**职责**

* 串联多个领域服务与仓储。
* 处理事务边界与异常。
* 实现高层用例（如生成ΔOΔG并对比迁移）。

**核心服务**

* `CompareService`: 生成 ΔO、ΔG。
* `MigrationService`: 对比 ΔO 与 ΔG。
* `GenerateService`: 处理生成、撤销、应用。

**好处**

* 高层业务流程集中。
* 隔离复杂底层实现。

---

### 3️⃣ `domain` 层 — 核心业务模型

| 子模块       | 内容                                    | 职责                 |
| --------- | ------------------------------------- | ------------------ |
| diff      | `DiffFile`, `DiffBlock`, `DeltaGroup` | 管理仓库内增量变化 ΔO、ΔG    |
| migration | `MigrationTask`, `MigrationResult`    | 比对 ΔO vs ΔG，生成迁移方案 |
| repo      | `RepoConfig`, `RepoBranch`            | 管理仓库与分支上下文         |
| common    | 值对象、范围、路径                             | 通用抽象               |
| setting   | 系统配置                                  | 全局参数保存             |

**好处**

* 与框架无关。
* 可单测验证核心逻辑。
* 方便扩展至其他版本库。

---

### 4️⃣ `infrastructure` 层 — 技术实现

**职责**

* 具体实现持久化、Git交互、配置。
* 实现领域接口（仓储、Adapter）。

**模块**

* `git`: 调用 JGit，生成 Diff。
* `persistence`: Repository 实现。
* `config`: 应用启动配置。
* `mapper`: ORM 映射层。

**好处**

* 技术细节可替换。
* 保持业务逻辑稳定。

---

### 5️⃣ `shared` 层 — 通用支撑模块

**职责**

* 提供全局异常、工具、常量。
* 不包含业务语义。

**模块**

* `exception`: 自定义异常。
* `utils`: JSON、文件、校验。
* `constants`: 系统常量。

**好处**

* 避免重复逻辑。
* 程序结构清晰。

---

## 📘 三、核心领域数据结构

### 仓库与分支（ΔO / ΔG 来源）

```java
public enum RepoType { ORACLE, GAUSS }

public class RepoPath {
    private String absolutePath;
    private RepoType type;
}

public class RepoBranch {
    private String name;      // o1, o2, g1, g2
    private String commitId;
}

public class RepoConfig {
    private RepoPath repoPath;
    private RepoBranch branchFrom;
    private RepoBranch branchTo;
}
```

---

### Diff 家族（文件与块差异）

```java
public enum DiffType { ADD, MODIFY, DELETE, RENAME }
public enum DeltaType { DELTA_O, DELTA_G }

public class DiffBlock {
    private int startLineFrom;
    private int endLineFrom;
    private int startLineTo;
    private int endLineTo;
    private String contentFrom;
    private String contentTo;
    private DiffType type;
}

public class DiffFile {
    private String relativePath;
    private List<DiffBlock> blocks;
    private DeltaType deltaType;
}

public class DiffSummary {
    private RepoConfig repoConfig;
    private List<DiffFile> diffFiles;
}
```

---

### ΔO / ΔG 差异融合

```java
public class DeltaGroup {
    private DiffFile deltaO;
    private DiffFile deltaG;
    private List<DiffBlock> intersectBlocks;  // 相同修改
    private List<DiffBlock> conflictBlocks;   // 不一致修改
    private List<DiffBlock> uniqueBlocks;     // 单边修改
}
```

---

### 迁移任务

```java
public enum MigrationStatus { PENDING, GENERATED, APPLIED, REVERTED }

public class MigrationTask {
    private String id;
    private String taskName;
    private LocalDateTime createdAt;
    private MigrationStatus status;
    private DeltaGroup deltaGroup;
}

public class MigrationResult {
    private MigrationTask task;
    private boolean success;
    private String message;
    private List<String> affectedFiles;
}

public class MigrationMapping {
    private String sourcePath;
    private String targetPath;
    private Map<Integer, Integer> lineMapping;
}
```

---

## 🧠 四、架构优点

| 特点     | 说明               |
| ------ | ---------------- |
| 高内聚低耦合 | 层间依赖方向单一，修改安全    |
| 技术无关   | 核心业务独立于框架        |
| 可扩展    | 新增规则或对比类型容易      |
| 可测试    | 各层可单元测试          |
| 可替换    | Git 可替换为其他版本控制系统 |
