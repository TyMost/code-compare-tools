# 时间区间扫描示例

本示例用于演示如何结合 `examples` 目录下的 Oracle/Gauss 预置仓库，通过“时间区间”方式触发 `/api/scan/full` 或 `/api/scan`。

## 请求体示例

`time-range-scan-request.json`：

```json
{
  "taskId": "demo-time-range-20251112",
  "persistResult": true,
  "presetName": "default-og",
  "oracle": {
    "repoPath": "D:/Coding/code-compare-tools/examples/o",
    "timeFrom": "2025-11-01T00:00:00+08:00",
    "timeTo": "2025-11-05T12:00:00+08:00",
    "refHint": "refs/heads/o2"
  },
  "gauss": {
    "repoPath": "D:/Coding/code-compare-tools/examples/g",
    "timeFrom": "2025-11-02T00:00:00+08:00",
    "timeTo": "2025-11-05T12:00:00+08:00",
    "refHint": "refs/heads/g2"
  }
}
```

## 调用方式

```powershell
$body = Get-Content examples/time-range-scan-request.json -Raw
Invoke-WebRequest -Uri 'http://localhost:8081/api/scan/full' `
  -Method Post -ContentType 'application/json' -Body $body |
  Select-Object -Expand Content
```

## 字段说明

- `timeFrom` / `timeTo`：ISO-8601 字符串，后端使用 JDK 8 `OffsetDateTime` 解析，可只填一端。
- `refHint`：解析时间范围时的参考引用，未填写会自动回退到分支名或 `HEAD`。填写明确分支可以减少遍历范围。
- `presetName`：继续复用 `default-og` 预设，保证仓库路径等参数与后端配置一致；如需自定义仓库可直接覆盖 `repoPath`。
- `persistResult`：若需后续查看 diff detail 或生成迁移脚本，请保持 `true`。

> 当前前端暂未开放时间区间配置；可直接使用上述方式通过脚本调用后端 API，以验证时间模式的行为。
