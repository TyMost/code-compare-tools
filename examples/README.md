# Git 示例数据（Oracle→Gauss 增量迁移）

本目录现在包含两类示例：
1. **传统演示**：`projectA-git`、`projectB-git`、`projectC-git` 继续服务旧版覆盖率/增量 Diff 演示。
2. **Oracle→Gauss 场景**：`_fixtures` 下预置 `o`、`g`、`g-extra` 三个分支快照，配合脚本生成实际 Git 仓库，以复现 `backend/docs/git-incremental-migration-scenarios.md` 中 S1~S12 的全部场景。

> 每个子目录都已经初始化为真实 Git 仓库，并预置好分支与提交。可直接运行 `git -C <目录> log --oneline --graph` 查看历史。

## 仓库与分支矩阵

| 仓库 | 路径 | 分支 | 角色 | 说明 |
| --- | --- | --- | --- | --- |
| `o` | `examples/o` | `o1`（基线）、`o2`（Oracle 增量） | Oracle 源系统 | `o1` 为共享业务底座，`o2` 覆盖 12 个场景中的源端改动（新增、删除、注解破坏等）。 |
| `g` | `examples/g` | `g1`（基线）、`g2`（Gauss 增量） | Gauss 目标系统 | `g1` 与 `o1` 基本一致但内置 15% Gauss 特有逻辑；`g2` 只吸收部分 Oracle 改动并新增 Gauss 独占特性。 |
| `g-extra` | `examples/g-extra` | `main`、`gauss-rd` | Gauss 补充仓库 | `gauss-rd` 分支包含损坏的 diff 元数据及二进制文档，用于批量比对压力测试。 |

仓库内的 Java 文件数量约占 70%，MyBatis XML 约 15%，其余为配置/脚本/二进制资产，满足数据占比约束。

### 如何初始化 Git 仓库
- 运行 `examples/setup-migration-fixtures.ps1`（PowerShell），即可把 `_fixtures/<repo>/<branch>` 快照展开到 `examples/o`、`examples/g`、`examples/g-extra` 并自动初始化各分支历史。
- 脚本具备幂等性：多次执行会先清空目标目录（保留 `.git`），再重新 checkout 对应分支。
- 如果需要清理演示仓库，可直接删除 `examples/o`、`examples/g`、`examples/g-extra`，然后重新运行脚本。

> `setup-migration-fixtures.ps1` 在初始化时会设定临时的 Git 用户信息 `Fixture Bot <fixtures@example.com>`，以免污染主仓库的配置。

## 目录骨架

`o` 与 `g` 仓库共用以下结构（`g-extra` 在此基础上增加 `playground` 及二进制目录）：

```
src/
  main/java/com/example/migration/
    common|customer|loan|reporting|sharding|annotation|legacy|inventory|billing|risk
  main/resources/
    mapper/{customer,billing,loan,legacy}
    config/
    sql/
  test/java/... (SmokeTest)
docs/
scripts/
```

## S1~S12 场景对照

| 场景 | Oracle diff（`o1→o2`） | Gauss/G-extra diff | 触发文件 |
| --- | --- | --- | --- |
| **S1 目标端新增** | — | `g2`: `src/main/java/.../GaussShardRoutingService.java` 与 `config/sharding/gauss-shard.yml` | `g` 仓库 | `g` 仅包含。 |
| **S2 源端删除** | 删除 `legacy/OracleArchiveJob.java` 与 `mapper/legacy/ArchiveMapper.xml` | — | `o` 仓库 | |
| **S3 双端修改** | `customer/CustomerSyncService.java`、`mapper/customer/CustomerMapper.xml` 新增周年标签逻辑 | `g2` 在同名文件增加忠诚度逻辑 | `o`、`g` | |
| **S4 源端新增** | 新增 `customer/RetentionCampaignService.java`、`mapper/customer/RetentionMapper.xml` | — | `o` | |
| **S5 目标端部分重叠** | `billing/SettlementProcessor.java` + `mapper/billing/SettlementMapper.xml` 拆分方法 | `g2` 在同文件增加 Gauss 特有审计字段但缺失 Oracle 新增的空窗控制 | `o`、`g` | |
| **S6 空 diff** | `reporting/ReportScheduler.java` 仅增加注释、`docs/reporting-faq.md` 追加说明 | — | `o` | |
| **S7 重命名候选** | `InventoryProjectionService` 重命名为 `InventoryProjectionCalculator` 并更新 `InventoryFacade` 引用 | `g` 仍保留旧文件名 | `o`、`g` | |
| **S8 注解冲突** | — | `g2`: `annotation/ChangeAuditAspect.java` 上重复 `@MigrationNote` | `g` | |
| **S9 扫描错误** | — | `g-extra:gauss-rd`：损坏的 `sql/plans/failed-plan.bin` 与被截断的 `src/main/resources/diff/plan.diffmeta` | `g-extra` | |
| **S10 模糊定位** | `mapper/loan/LoanAdjustmentMapper.xml` 含两段 `<update id="repriceLoan">` | — | `o` | |
| **S11 注解破坏** | `annotation/OracleChangeMarker.java` 将 `${code}` 错写为 `%{code}%` | — | `o` | |
| **S12 不支持的 diff** | — | `g-extra:gauss-rd` 新增二进制 `docs/migration-audit.xlsx` | `g-extra` | |

> 其余 Java 与 XML 文件提供语境背景，方便 git diff 生成富文本块。

## 批量 git-comparison 配置

`examples/git-comparison-batch.yaml` 已更新为两个核心配对：

1. **Pair #1：`o` → `g`**  
   - source：`examples/o`，`git diff o1..o2`
   - target：`examples/g`，`git diff g1..g2`
   - 用途：主路径验证 Oracle 增量在 Gauss 分支的对齐情况。
2. **Pair #2：`o` → `g-extra`**  
   - source 同上（`o1` → `o2`）
   - target：`examples/g-extra`，`git diff main..gauss-rd`
   - 用途：将真实业务 diff 与异常资产并排，用于标签器/agent 的异常兜底测试。

可在项目根目录执行以下命令快速验证：

```powershell
mvn -f backend/pom.xml -DskipTests exec:java -Dexec.args="--batch git-comparison --config examples/git-comparison-batch.yaml"
```

或在 UI 中配置 `migration.scan.project.sources/targets` 指向 `examples/o` 与 `examples/g*`，并将 `migration.scan.diffEngine` 切换为 `git`。

## 使用建议

1. 如需重置示例，可在每个仓库执行 `git checkout <基线分支> -- .` 并 `git clean -fd`。
2. 运行 `git diff o1 o2 --stat` 等命令即可直观看到各场景差异。
3. 若要扩展场景，可在相应分支新增文件，再更新本文档与批量配置保持同步。

祝使用顺利！
