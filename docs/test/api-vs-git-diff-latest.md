# 最新接口与 Git Diff 对比
任务 ID：codex-verify-20251103-8081

## /api/scan/full 摘要
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "persistResult": true,
    "oracle": {
        "repoPath": "D:/Coding/code-compare-tools/examples/o",
        "branchFrom": "o1",
        "branchTo": "o2",
        "deltaType": "DELTA_O"
    },
    "gauss": {
        "repoPath": "D:/Coding/code-compare-tools/examples/g",
        "branchFrom": "g1",
        "branchTo": "g2",
        "deltaType": "DELTA_G"
    }
}
```
**出参**
```json
{
    "status": "success",
    "message": "",
    "data": {
        "taskId": "codex-verify-20251103-8081",
        "summary": {
            "totalFiles": 17,
            "oracleOnly": 10,
            "gaussOnly": 3,
            "matched": 0,
            "consistencyRate": 0.18
        },
        "diffMatrix": [
            {
                "filePath": "docs/reporting-faq.md",
                "oracleDelta": "+4/-4",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/annotation/ChangeAuditAspect.java",
                "oracleDelta": "+0/-0",
                "gaussDelta": "+7/-7",
                "coverage": 0.0,
                "status": "gauss-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/annotation/OracleChangeMarker.java",
                "oracleDelta": "+6/-6",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
                "oracleDelta": "+23/-23",
                "gaussDelta": "+24/-24",
                "coverage": 0.45,
                "status": "partial"
            },
            {
                "filePath": "src/main/java/com/example/migration/customer/CustomerSyncService.java",
                "oracleDelta": "+33/-33",
                "gaussDelta": "+31/-31",
                "coverage": 0.6,
                "status": "partial"
            },
            {
                "filePath": "src/main/java/com/example/migration/customer/RetentionCampaignService.java",
                "oracleDelta": "+17/-0",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/inventory/InventoryFacade.java",
                "oracleDelta": "+10/-10",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/inventory/InventoryProjectionCalculator.java",
                "oracleDelta": "+6/-6",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/legacy/OracleArchiveJob.java",
                "oracleDelta": "+0/-7",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/reporting/ReportScheduler.java",
                "oracleDelta": "+7/-7",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/java/com/example/migration/sharding/GaussShardRoutingService.java",
                "oracleDelta": "+0/-0",
                "gaussDelta": "+13/-0",
                "coverage": 0.0,
                "status": "gauss-only"
            },
            {
                "filePath": "src/main/resources/config/sharding/gauss-shard.yml",
                "oracleDelta": "+0/-0",
                "gaussDelta": "+7/-0",
                "coverage": 0.0,
                "status": "gauss-only"
            },
            {
                "filePath": "src/main/resources/mapper/billing/SettlementMapper.xml",
                "oracleDelta": "+9/-9",
                "gaussDelta": "+13/-13",
                "coverage": 0.24,
                "status": "partial"
            },
            {
                "filePath": "src/main/resources/mapper/customer/CustomerMapper.xml",
                "oracleDelta": "+16/-16",
                "gaussDelta": "+10/-10",
                "coverage": 0.79,
                "status": "partial"
            },
            {
                "filePath": "src/main/resources/mapper/customer/RetentionMapper.xml",
                "oracleDelta": "+9/-0",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/resources/mapper/legacy/ArchiveMapper.xml",
                "oracleDelta": "+0/-8",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            },
            {
                "filePath": "src/main/resources/mapper/loan/LoanAdjustmentMapper.xml",
                "oracleDelta": "+10/-10",
                "gaussDelta": "+0/-0",
                "coverage": 0.0,
                "status": "oracle-only"
            }
        ]
    }
}
```

## /api/scan (增量)
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081-incremental",
    "persistResult": false,
    "oracle": {
        "repoPath": "D:/Coding/code-compare-tools/examples/o",
        "branchFrom": "o1",
        "branchTo": "o2"
    },
    "gauss": {
        "repoPath": "D:/Coding/code-compare-tools/examples/g",
        "branchFrom": "g1",
        "branchTo": "g2"
    }
}
```
**出参**
```json
{
    "status": "success",
    "message": "",
    "data": {
        "taskId": "codex-verify-20251103-8081-incremental",
        "summary": {
            "totalFiles": 0,
            "oracleOnly": 0,
            "gaussOnly": 0,
            "matched": 0,
            "consistencyRate": 1.0
        },
        "diffMatrix": []
    }
}
```

## SettlementProcessor.java
### /api/scan/detail
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java"
}
```
**出参**
```json
{
    "status": "success",
    "message": "",
    "data": {
        "taskId": "codex-verify-20251103-8081",
        "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
        "oracleDiff": {
            "before": "package com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\npublic class SettlementProcessor {\n    public String settle(RequestContext ctx, String windowId) {\n        return \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n    }\n}",
            "after": "package com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\nimport java.util.ArrayList;\nimport java.util.List;\npublic class SettlementProcessor {\n    private final List<String> auditTrail = new ArrayList<>();\n\n    public String settle(RequestContext ctx, String windowId) {\n        String result = \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n        auditTrail.add(result);\n        return result;\n    }\n\n    public void splitWindow(RequestContext ctx, String windowId) {\n        auditTrail.add(\"SPLIT:\" + windowId + \":\" + ctx.getOperator());\n    }\n\n    public List<String> getAuditTrail() {\n        return auditTrail;\n    }\n}"
        },
        "gaussDiff": {
            "before": "package com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\npublic class SettlementProcessor {\n    public String settle(RequestContext ctx, String windowId) {\n        return \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n    }\n}",
            "after": "package com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\nimport java.time.OffsetDateTime;\nimport java.util.ArrayList;\nimport java.util.List;\npublic class SettlementProcessor {\n    private final List<String> auditTrail = new ArrayList<>();\n\n    public String settle(RequestContext ctx, String windowId) {\n        String result = \"GAUSS_SETTLED:\" + windowId + \":\" + ctx.getOperator();\n        auditTrail.add(result + \":\" + OffsetDateTime.now());\n        return result;\n    }\n\n    public void splitWindow(RequestContext ctx, String windowId) {\n        auditTrail.add(\"SPLIT_GAUSS:\" + windowId + \":\" + ctx.getOperator());\n    }\n\n    public List<String> getAuditTrail() {\n        return auditTrail;\n    }\n}"
        },
        "migrationDiff": "/** 迁移适配开始\r\n * 请按照 project_rules.md 修改适配\r\n */\r\n/** 目标仓库原实现开始 */\r\npackage com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\nimport java.util.ArrayList;\nimport java.util.List;\npublic class SettlementProcessor {\n    private final List<String> auditTrail = new ArrayList<>();\n\n    public String settle(RequestContext ctx, String windowId) {\n        String result = \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n        auditTrail.add(result);\n        return result;\n    }\n\n    public void splitWindow(RequestContext ctx, String windowId) {\n        auditTrail.add(\"SPLIT:\" + windowId + \":\" + ctx.getOperator());\n    }\n\n    public List<String> getAuditTrail() {\n        return auditTrail;\n    }\n}\r\n/** 目标仓库原实现结束 */\r\n/** 迁移适配建议开始 */\r\npackage com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\npublic class SettlementProcessor {\n    public String settle(RequestContext ctx, String windowId) {\n        return \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n    }\n}\r\n/** 迁移适配建议结束 */\r\n/** 迁移适配段结束 */",
        "stats": {
            "oracleAdded": 23,
            "oracleRemoved": 23,
            "gaussAdded": 24,
            "gaussRemoved": 24
        },
        "coverage": 0.45
    }
}
```

### /api/migrate/generate
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java"
}
```
**出参**
```json
{
    "status": "success",
    "message": "Migration diff generated",
    "data": {
        "taskId": "codex-verify-20251103-8081",
        "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
        "migrationDiff": "/** 迁移适配开始\r\n * 请按照 project_rules.md 修改适配\r\n */\r\n/** 目标仓库原实现开始 */\r\npackage com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\nimport java.util.ArrayList;\nimport java.util.List;\npublic class SettlementProcessor {\n    private final List<String> auditTrail = new ArrayList<>();\n\n    public String settle(RequestContext ctx, String windowId) {\n        String result = \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n        auditTrail.add(result);\n        return result;\n    }\n\n    public void splitWindow(RequestContext ctx, String windowId) {\n        auditTrail.add(\"SPLIT:\" + windowId + \":\" + ctx.getOperator());\n    }\n\n    public List<String> getAuditTrail() {\n        return auditTrail;\n    }\n}\r\n/** 目标仓库原实现结束 */\r\n/** 迁移适配建议开始 */\r\npackage com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\npublic class SettlementProcessor {\n    public String settle(RequestContext ctx, String windowId) {\n        return \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n    }\n}\r\n/** 迁移适配建议结束 */\r\n/** 迁移适配段结束 */\r\n\r\n/** 迁移适配开始\r\n * 请按照 project_rules.md 修改适配\r\n */\r\n/** 目标仓库原实现开始 */\r\npackage com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\nimport java.time.OffsetDateTime;\nimport java.util.ArrayList;\nimport java.util.List;\npublic class SettlementProcessor {\n    private final List<String> auditTrail = new ArrayList<>();\n\n    public String settle(RequestContext ctx, String windowId) {\n        String result = \"GAUSS_SETTLED:\" + windowId + \":\" + ctx.getOperator();\n        auditTrail.add(result + \":\" + OffsetDateTime.now());\n        return result;\n    }\n\n    public void splitWindow(RequestContext ctx, String windowId) {\n        auditTrail.add(\"SPLIT_GAUSS:\" + windowId + \":\" + ctx.getOperator());\n    }\n\n    public List<String> getAuditTrail() {\n        return auditTrail;\n    }\n}\r\n/** 目标仓库原实现结束 */\r\n/** 迁移适配建议开始 */\r\npackage com.example.migration.billing;\nimport com.example.migration.common.RequestContext;\npublic class SettlementProcessor {\n    public String settle(RequestContext ctx, String windowId) {\n        return \"SETTLED:\" + windowId + \":\" + ctx.getOperator();\n    }\n}\r\n/** 迁移适配建议结束 */\r\n/** 迁移适配段结束 */"
    }
}
```

### /api/migrate/apply
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java"
}
```
**出参**
```json
{
    "status": "success",
    "message": "Migration applied",
    "data": {
        "taskId": "codex-verify-20251103-8081",
        "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
        "logId": 6954069908878453882
    }
}
```

### /api/migrate/revert
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java"
}
```
**出参**
```json
{
    "status": "success",
    "message": "Migration reverted",
    "data": null
}
```

### git diff Oracle (o1..o2)
```diff
diff --git a/src/main/java/com/example/migration/billing/SettlementProcessor.java b/src/main/java/com/example/migration/billing/SettlementProcessor.java
index 4f9ac8f..24ce5ed 100644
--- a/src/main/java/com/example/migration/billing/SettlementProcessor.java
+++ b/src/main/java/com/example/migration/billing/SettlementProcessor.java
@@ -1,9 +1,23 @@
 ﻿package com.example.migration.billing;
 
 import com.example.migration.common.RequestContext;
+import java.util.ArrayList;
+import java.util.List;
 
 public class SettlementProcessor {
+    private final List<String> auditTrail = new ArrayList<>();
+
     public String settle(RequestContext ctx, String windowId) {
-        return "SETTLED:" + windowId + ":" + ctx.getOperator();
+        String result = "SETTLED:" + windowId + ":" + ctx.getOperator();
+        auditTrail.add(result);
+        return result;
+    }
+
+    public void splitWindow(RequestContext ctx, String windowId) {
+        auditTrail.add("SPLIT:" + windowId + ":" + ctx.getOperator());
+    }
+
+    public List<String> getAuditTrail() {
+        return auditTrail;
     }
 }
```

### git diff Gauss (g1..g2)
```diff
diff --git a/src/main/java/com/example/migration/billing/SettlementProcessor.java b/src/main/java/com/example/migration/billing/SettlementProcessor.java
index 4f9ac8f..a21ff6c 100644
--- a/src/main/java/com/example/migration/billing/SettlementProcessor.java
+++ b/src/main/java/com/example/migration/billing/SettlementProcessor.java
@@ -1,9 +1,24 @@
 ﻿package com.example.migration.billing;
 
 import com.example.migration.common.RequestContext;
+import java.time.OffsetDateTime;
+import java.util.ArrayList;
+import java.util.List;
 
 public class SettlementProcessor {
+    private final List<String> auditTrail = new ArrayList<>();
+
     public String settle(RequestContext ctx, String windowId) {
-        return "SETTLED:" + windowId + ":" + ctx.getOperator();
+        String result = "GAUSS_SETTLED:" + windowId + ":" + ctx.getOperator();
+        auditTrail.add(result + ":" + OffsetDateTime.now());
+        return result;
+    }
+
+    public void splitWindow(RequestContext ctx, String windowId) {
+        auditTrail.add("SPLIT_GAUSS:" + windowId + ":" + ctx.getOperator());
+    }
+
+    public List<String> getAuditTrail() {
+        return auditTrail;
     }
 }
```

## CustomerSyncService.java
### /api/scan/detail
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "filePath": "src/main/java/com/example/migration/customer/CustomerSyncService.java"
}
```
**出参**
```json
{
    "status": "success",
    "message": "",
    "data": {
        "taskId": "codex-verify-20251103-8081",
        "filePath": "src/main/java/com/example/migration/customer/CustomerSyncService.java",
        "oracleDiff": {
            "before": "package com.example.migration.customer;\nimport com.example.migration.common.RequestContext;\nimport java.util.List;\nimport java.util.concurrent.CopyOnWriteArrayList;\npublic class CustomerSyncService {\n    private final List<String> operations = new CopyOnWriteArrayList<>();\n    public void syncCustomer(RequestContext ctx, String customerId) {\n        operations.add(\"SYNC:\" + customerId + \":\" + ctx.getOperator());\n    }\n    public List<String> getOperations() {\n        return operations;\n    }\n}",
            "after": "package com.example.migration.customer;\nimport com.example.migration.common.RequestContext;\nimport java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.concurrent.CopyOnWriteArrayList;\npublic class CustomerSyncService {\n    private final List<String> operations = new CopyOnWriteArrayList<>();\n    private final Map<String, String> annotations = new LinkedHashMap<>();\n    public void syncCustomer(RequestContext ctx, String customerId) {\n        operations.add(\"SYNC:\" + customerId + \":\" + ctx.getOperator());\n        if (customerId.endsWith(\"VIP\")) {\n            annotations.put(customerId, \"ANNIVERSARY\");\n        }\n    }\n\n    public void refreshAnnotations(RequestContext ctx, List<String> customerIds) {\n        for (String id : customerIds) {\n            annotations.put(id, \"REFRESHED:\" + ctx.getOperator());\n        }\n    }\n    public List<String> getOperations() {\n        return operations;\n    }\n\n    public Map<String, String> getAnnotations() {\n        return annotations;\n    }\n}"
        },
        "gaussDiff": {
            "before": "package com.example.migration.customer;\nimport com.example.migration.common.RequestContext;\nimport java.util.List;\nimport java.util.concurrent.CopyOnWriteArrayList;\npublic class CustomerSyncService {\n    private final List<String> operations = new CopyOnWriteArrayList<>();\n    public void syncCustomer(RequestContext ctx, String customerId) {\n        operations.add(\"SYNC:\" + customerId + \":\" + ctx.getOperator());\n    }\n    public List<String> getOperations() {\n        return operations;\n    }\n}",
            "after": "package com.example.migration.customer;\nimport com.example.migration.common.RequestContext;\nimport java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.concurrent.CopyOnWriteArrayList;\npublic class CustomerSyncService {\n    private final List<String> operations = new CopyOnWriteArrayList<>();\n    private final Map<String, String> annotations = new LinkedHashMap<>();\n    public void syncCustomer(RequestContext ctx, String customerId) {\n        operations.add(\"GAUSS_SYNC:\" + customerId + \":\" + ctx.getOperator());\n        annotations.put(customerId, \"GAUSS_TRACKING\");\n    }\n\n    public void pushLoyaltyBadge(RequestContext ctx, List<String> customerIds) {\n        for (String id : customerIds) {\n            annotations.put(id, \"LOYALTY:\" + ctx.getOperator());\n        }\n    }\n    public List<String> getOperations() {\n        return operations;\n    }\n\n    public Map<String, String> getAnnotations() {\n        return annotations;\n    }\n}"
        },
        "migrationDiff": "/** 迁移适配开始\r\n * 请按照 project_rules.md 修改适配\r\n */\r\n/** 目标仓库原实现开始 */\r\npackage com.example.migration.customer;\nimport com.example.migration.common.RequestContext;\nimport java.util.LinkedHashMap;\nimport java.util.List;\nimport java.util.Map;\nimport java.util.concurrent.CopyOnWriteArrayList;\npublic class CustomerSyncService {\n    private final List<String> operations = new CopyOnWriteArrayList<>();\n    private final Map<String, String> annotations = new LinkedHashMap<>();\n    public void syncCustomer(RequestContext ctx, String customerId) {\n        operations.add(\"SYNC:\" + customerId + \":\" + ctx.getOperator());\n        if (customerId.endsWith(\"VIP\")) {\n            annotations.put(customerId, \"ANNIVERSARY\");\n        }\n    }\n\n    public void refreshAnnotations(RequestContext ctx, List<String> customerIds) {\n        for (String id : customerIds) {\n            annotations.put(id, \"REFRESHED:\" + ctx.getOperator());\n        }\n    }\n    public List<String> getOperations() {\n        return operations;\n    }\n\n    public Map<String, String> getAnnotations() {\n        return annotations;\n    }\n}\r\n/** 目标仓库原实现结束 */\r\n/** 迁移适配建议开始 */\r\npackage com.example.migration.customer;\nimport com.example.migration.common.RequestContext;\nimport java.util.List;\nimport java.util.concurrent.CopyOnWriteArrayList;\npublic class CustomerSyncService {\n    private final List<String> operations = new CopyOnWriteArrayList<>();\n    public void syncCustomer(RequestContext ctx, String customerId) {\n        operations.add(\"SYNC:\" + customerId + \":\" + ctx.getOperator());\n    }\n    public List<String> getOperations() {\n        return operations;\n    }\n}\r\n/** 迁移适配建议结束 */\r\n/** 迁移适配段结束 */",
        "stats": {
            "oracleAdded": 33,
            "oracleRemoved": 33,
            "gaussAdded": 31,
            "gaussRemoved": 31
        },
        "coverage": 0.6
    }
}
```

### git diff Oracle (o1..o2)
```diff
diff --git a/src/main/java/com/example/migration/customer/CustomerSyncService.java b/src/main/java/com/example/migration/customer/CustomerSyncService.java
index 9dade2b..261c733 100644
--- a/src/main/java/com/example/migration/customer/CustomerSyncService.java
+++ b/src/main/java/com/example/migration/customer/CustomerSyncService.java
@@ -1,17 +1,33 @@
 ﻿package com.example.migration.customer;
 
 import com.example.migration.common.RequestContext;
+import java.util.LinkedHashMap;
 import java.util.List;
+import java.util.Map;
 import java.util.concurrent.CopyOnWriteArrayList;
 
 public class CustomerSyncService {
     private final List<String> operations = new CopyOnWriteArrayList<>();
+    private final Map<String, String> annotations = new LinkedHashMap<>();
 
     public void syncCustomer(RequestContext ctx, String customerId) {
         operations.add("SYNC:" + customerId + ":" + ctx.getOperator());
+        if (customerId.endsWith("VIP")) {
+            annotations.put(customerId, "ANNIVERSARY");
+        }
+    }
+
+    public void refreshAnnotations(RequestContext ctx, List<String> customerIds) {
+        for (String id : customerIds) {
+            annotations.put(id, "REFRESHED:" + ctx.getOperator());
+        }
     }
 
     public List<String> getOperations() {
         return operations;
     }
+
+    public Map<String, String> getAnnotations() {
+        return annotations;
+    }
 }
```

### git diff Gauss (g1..g2)
```diff
diff --git a/src/main/java/com/example/migration/customer/CustomerSyncService.java b/src/main/java/com/example/migration/customer/CustomerSyncService.java
index 9dade2b..177f458 100644
--- a/src/main/java/com/example/migration/customer/CustomerSyncService.java
+++ b/src/main/java/com/example/migration/customer/CustomerSyncService.java
@@ -1,17 +1,31 @@
 ﻿package com.example.migration.customer;
 
 import com.example.migration.common.RequestContext;
+import java.util.LinkedHashMap;
 import java.util.List;
+import java.util.Map;
 import java.util.concurrent.CopyOnWriteArrayList;
 
 public class CustomerSyncService {
     private final List<String> operations = new CopyOnWriteArrayList<>();
+    private final Map<String, String> annotations = new LinkedHashMap<>();
 
     public void syncCustomer(RequestContext ctx, String customerId) {
-        operations.add("SYNC:" + customerId + ":" + ctx.getOperator());
+        operations.add("GAUSS_SYNC:" + customerId + ":" + ctx.getOperator());
+        annotations.put(customerId, "GAUSS_TRACKING");
+    }
+
+    public void pushLoyaltyBadge(RequestContext ctx, List<String> customerIds) {
+        for (String id : customerIds) {
+            annotations.put(id, "LOYALTY:" + ctx.getOperator());
+        }
     }
 
     public List<String> getOperations() {
         return operations;
     }
+
+    public Map<String, String> getAnnotations() {
+        return annotations;
+    }
 }
```

## SettlementMapper.xml
### /api/scan/detail
**入参**
```json
{
    "taskId": "codex-verify-20251103-8081",
    "filePath": "src/main/resources/mapper/billing/SettlementMapper.xml"
}
```
**出参**
```json
{
    "status": "success",
    "message": "",
    "data": {
        "taskId": "codex-verify-20251103-8081",
        "filePath": "src/main/resources/mapper/billing/SettlementMapper.xml",
        "oracleDiff": {
            "before": "SET window_flag = #{flag}\n        WHERE window_id = #{windowId}\n    </update>\n</mapper>",
            "after": "SET window_flag = #{flag}\n        WHERE window_id = #{windowId}\n    </update>\n\n    <insert id=\"insertSettlementEvent\">\n        INSERT INTO billing_audit(window_id, operator)\n        VALUES(#{windowId}, #{operator})\n    </insert>\n</mapper>"
        },
        "gaussDiff": {
            "before": "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n<mapper namespace=\"com.example.migration.billing.SettlementMapper\">\n    <update id=\"splitSettlementWindow\">\n        UPDATE billing_window\n        SET window_flag = #{flag}\n        WHERE window_id = #{windowId}\n    </update>\n</mapper>",
            "after": "<!DOCTYPE mapper PUBLIC \"-//mybatis.org//DTD Mapper 3.0//EN\" \"http://mybatis.org/dtd/mybatis-3-mapper.dtd\">\n<mapper namespace=\"com.example.migration.billing.SettlementMapper\">\n    <update id=\"splitSettlementWindow\">\n        UPDATE gauss_billing_window\n        SET window_flag = #{flag}, shard_hint = #{shardHint}\n        WHERE window_id = #{windowId}\n    </update>\n\n    <insert id=\"insertSettlementEvent\">\n        INSERT INTO gauss_billing_audit(window_id, operator)\n        VALUES(#{windowId}, #{operator})\n    </insert>\n</mapper>"
        },
        "migrationDiff": "/** 迁移适配开始\r\n * 请按照 project_rules.md 修改适配\r\n */\r\n/** 目标仓库原实现开始 */\r\n        SET window_flag = #{flag}\n        WHERE window_id = #{windowId}\n    </update>\n\n    <insert id=\"insertSettlementEvent\">\n        INSERT INTO billing_audit(window_id, operator)\n        VALUES(#{windowId}, #{operator})\n    </insert>\n</mapper>\r\n/** 目标仓库原实现结束 */\r\n/** 迁移适配建议开始 */\r\n        SET window_flag = #{flag}\n        WHERE window_id = #{windowId}\n    </update>\n</mapper>\r\n/** 迁移适配建议结束 */\r\n/** 迁移适配段结束 */",
        "stats": {
            "oracleAdded": 9,
            "oracleRemoved": 9,
            "gaussAdded": 13,
            "gaussRemoved": 13
        },
        "coverage": 0.24
    }
}
```

### git diff Oracle (o1..o2)
```diff
diff --git a/src/main/resources/mapper/billing/SettlementMapper.xml b/src/main/resources/mapper/billing/SettlementMapper.xml
index b4295a9..c7e0c85 100644
--- a/src/main/resources/mapper/billing/SettlementMapper.xml
+++ b/src/main/resources/mapper/billing/SettlementMapper.xml
@@ -6,4 +6,9 @@
         SET window_flag = #{flag}
         WHERE window_id = #{windowId}
     </update>
+
+    <insert id="insertSettlementEvent">
+        INSERT INTO billing_audit(window_id, operator)
+        VALUES(#{windowId}, #{operator})
+    </insert>
 </mapper>
```

### git diff Gauss (g1..g2)
```diff
diff --git a/src/main/resources/mapper/billing/SettlementMapper.xml b/src/main/resources/mapper/billing/SettlementMapper.xml
index b4295a9..de8f6e7 100644
--- a/src/main/resources/mapper/billing/SettlementMapper.xml
+++ b/src/main/resources/mapper/billing/SettlementMapper.xml
@@ -2,8 +2,13 @@
 <!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
 <mapper namespace="com.example.migration.billing.SettlementMapper">
     <update id="splitSettlementWindow">
-        UPDATE billing_window
-        SET window_flag = #{flag}
+        UPDATE gauss_billing_window
+        SET window_flag = #{flag}, shard_hint = #{shardHint}
         WHERE window_id = #{windowId}
     </update>
+
+    <insert id="insertSettlementEvent">
+        INSERT INTO gauss_billing_audit(window_id, operator)
+        VALUES(#{windowId}, #{operator})
+    </insert>
 </mapper>
```

## docs/reporting-faq.md
### git diff Oracle (o1..o2)
```diff
diff --git a/docs/reporting-faq.md b/docs/reporting-faq.md
index 60a88cd..0ab5152 100644
--- a/docs/reporting-faq.md
+++ b/docs/reporting-faq.md
@@ -1,3 +1,4 @@
 ﻿# Reporting FAQ
 
 - Keep the Oracle scheduler active until Gauss migration completes.
+- 增量迁移期间请勿关闭旧版任务，避免 batch 触发空窗。
```

### git diff Gauss (g1..g2)
目标分支无变更。

## 扫描配置与仓库接口
### /api/scan/presets
**入参** 无
**出参**
```json
{
    "status": "success",
    "message": "",
    "data": [
        {
            "name": "default-og",
            "source": {
                "code": "o-g",
                "path": "D:\\Coding\\code-compare-tools\\examples\\o",
                "branchFrom": "refs/heads/o1",
                "branchTo": "refs/heads/o2",
                "deltaType": "DELTA_O",
                "includeWorkingTree": false,
                "fetchIfMissing": true,
                "remoteName": "origin"
            },
            "target": {
                "code": "g",
                "path": "D:\\Coding\\code-compare-tools\\examples\\g",
                "branchFrom": "refs/heads/g1",
                "branchTo": "refs/heads/g2",
                "deltaType": "DELTA_G",
                "includeWorkingTree": false,
                "fetchIfMissing": true,
                "remoteName": "origin"
            }
        }
    ]
}
```

### /api/repos (GET)
**入参** 无
**出参**
```json
[]
```

### /api/repos (POST)
**入参**
```json
{
    "repoPath": {
        "absolutePath": "D:/Coding/code-compare-tools/examples/o",
        "type": "ORACLE"
    },
    "branchFrom": {
        "name": "o1"
    },
    "branchTo": {
        "name": "o2"
    },
    "deltaType": "DELTA_O",
    "fetchIfMissing": true,
    "includeWorkingTree": false,
    "remoteName": "origin"
}
```
**出参**
```json
{
    "repoPath": {
        "absolutePath": "D:/Coding/code-compare-tools/examples/o",
        "type": "ORACLE"
    },
    "branchFrom": {
        "name": "o1",
        "commitId": null
    },
    "branchTo": {
        "name": "o2",
        "commitId": null
    },
    "deltaType": "DELTA_O",
    "fetchIfMissing": true,
    "includeWorkingTree": false,
    "remoteName": "origin"
}
```

### /api/repos/branches (POST)
**入参**
```json
{
    "repoPath": {
        "absolutePath": "D:/Coding/code-compare-tools/examples/o",
        "type": "ORACLE"
    },
    "branchFrom": {
        "name": "o1"
    },
    "branchTo": {
        "name": "o2"
    },
    "deltaType": "DELTA_O",
    "fetchIfMissing": true,
    "includeWorkingTree": false,
    "remoteName": "origin"
}
```
**出参**
```json
[
    {
        "name": "o1",
        "commitId": "01ffc0af71f9557495f216d15939544fb71f0cbd"
    },
    {
        "name": "o2",
        "commitId": "6305861bed1385ac1b9b424088593f791919305a"
    }
]
```

## 系统设置接口
### /api/settings (POST)
**入参**
```json
{
    "key": "migration.defaultPreset",
    "value": "oracle-gauss-demo",
    "description": "Default preset captured during verification"
}
```
**出参**
```json
{
    "key": "migration.defaultPreset",
    "value": "oracle-gauss-demo",
    "description": "Default preset captured during verification"
}
```

### /api/settings/{key} (GET)
**入参** 无
**出参**
```json
{
    "key": "migration.defaultPreset",
    "value": "oracle-gauss-demo",
    "description": "Default preset captured during verification"
}
```
