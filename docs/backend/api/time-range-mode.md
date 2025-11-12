# 扫描接口时间区间模式示例

后端已支持在 `DiffRequestDTO` 中通过 `timeFrom`、`timeTo`、`refHint` 描述要比对的提交时间窗口。该能力兼容现有预设，前端暂未开放配置，可通过脚本或调试工具直接调用 REST 接口。

## 请求体示例
```json
{
  "taskId": "doc-demo-20241103-time",
  "persistResult": true,
  "presetName": "default-og",
  "oracle": {
    "timeFrom": "2025-11-01T00:00:00+08:00",
    "timeTo": "2025-11-05T12:00:00+08:00",
    "refHint": "refs/heads/o2"
  },
  "gauss": {
    "timeFrom": "2025-11-02T00:00:00+08:00",
    "timeTo": "2025-11-05T12:00:00+08:00",
    "refHint": "refs/heads/g2"
  }
}
```

## 说明
- `timeFrom`、`timeTo`：ISO-8601 字符串，JDK 8 `OffsetDateTime` 可解析，支持只提供某一端。
- `refHint`：按时间遍历时的参考分支/标签，未填写会自动回退到 `branchFrom`/`branchTo` 或 `HEAD`。
- `branchFrom`、`branchTo`：可省略；若同时提供，后端会优先按分支解析，失败后退回时间窗口（HYBRID 模式）。
- `fetchIfMissing`：保持默认 `true` 时，时间模式会在比对前自动 `git fetch`，确保新增提交可被 `RevWalk` 读取。
