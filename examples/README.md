# 示例项目说明

`examples/` 目录用于演示 `rules.yaml` 中的迁移标签如何在真实代码块上命中。目录下维护一对源/目标仓库，以及两份带 Git 历史的演示仓库：
- `projectA/`：迁移前的基线工程，保留 `LEGACY::` 前缀、`token.substring` 等旧实现。
- `projectB/`：迁移后的目标工程，应用 `encoder.encode / encoder.decodeTenant`、`override` 等新实现。
- `projectA-git/`、`projectB-git/`：与上述目录内容一致，但会通过 `init-git-history.sh` 初始化真实 Git 历史，便于演示增量扫描（如 `gitBaseRefSource/Target` 对比）。
- `manifest.yaml`：根据代码行数统计出的分类结果，数据直接对应后端的规则标签。

代码统计以“代码块”为单位（即同名文件在 A/B 仓库的合计行数），并按照 `rules.yaml` 的标签做聚合。“约等于”指标来自于 `manifest.yaml` 的 `actualPercent` 字段。

## 分类占比概览（按代码行数）
| 分类             | 目标占比 | 实际行数 | 实际占比 | 代表代码块 |
| ---------------- | -------- | -------- | -------- | ---------- |
| 已迁移           | ≈60%     | 283      | 56.37%   | `common/EncodingGateway.java`、`dashboard/DashboardAggregator.java`、`workflow/ApprovalWorkflow.java` |
| 语法改造         | ≈19%     | 98       | 19.52%   | `order/DiscountCalculator.java`、`order/OrderMapper.java` |
| 分片改造         | ≈10%     | 56       | 11.16%   | `shard/TenantShardResolver.java` |
| 配置改造         | ≈5%      | 26       | 5.18%    | `config/CheckoutConfiguration.java`、`resources/config/checkout-feature.yml` |
| 未迁移           | ≈3%      | 15       | 2.99%    | `legacy/LegacyPromotionService.java` |
| 其他（剩余部分） | ≈3%      | 24       | 4.78%    | `integration/PlatformBridge.java` |

> 统计总行数为 502 行，误差控制在 ±3% 范围内，满足“约等于”的要求。

## 对应规则速查

| 规则 ID                    | 说明                           | 典型命中文件                                     |
| -------------------------- | ------------------------------ | ------------------------------------------------ |
| `replace-syntax`           | `LEGACY::` → `encoder.encode`  | `order/DiscountCalculator.java`、`order/OrderMapper.java` |
| `replace-decode`           | `token.substring` → `encoder.decodeTenant` | `shard/TenantShardResolver.java` |
| `feature-flag-refactor`    | `setNewCheckout` → `override`  | `config/CheckoutConfiguration.java` |
| `channel-mapping-refactor` | `new HashMap` → `Map.copyOf`   | `integration/ChannelMappingRegistry.java` |
| `similarity-high`          | 高相似度命中“已迁移”           | `common/EncodingGateway.java` 等 |
| `similarity-low`           | 低相似度命中“未迁移”           | `legacy/LegacyPromotionService.java` |

## 使用建议

1. 运行 `examples/init-git-history.sh` 可为四个项目写入演示用 Git 历史（若需重置请先删除对应 `.git` 目录）。
2. 调整规则后，先更新 `manifest.yaml` 中的行数与占比，再运行后端扫描以核对标签命中情况。
3. 新增示例时，请确保：
   - 同名文件在 `projectA/` 与 `projectB/`（或对应 git 版本）下均存在（未迁移项例外）。
   - 将新增代码块归入上述分类之一，并更新 `manifest.yaml` 与本 README。
4. 若需要扩展“其他”占比，可在 `integration/` 或 `innovation/` 目录添加保持最小改动的样例。
