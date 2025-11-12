# Migratediff 后端接口手册

本文档总结 `backend\src\main\java\com\example\migratediff\api\controller` 中暴露的 REST 接口，并基于实际运行�?`http://localhost:8081` 的后端完成了验证。示例请求使�?PowerShell `Invoke-WebRequest`，仓库对比样例来�?`examples\o` �?`examples\g` 两套工程�?
> 运行环境约定  
> - 已启动后端：`http://localhost:8081`  
> - 示例仓库�? 
>   - Oracle 侧：`D:\Coding\code-compare-tools\examples\o`（分�?`o1` �?`o2`�? 
>   - Gauss 侧：`D:\Coding\code-compare-tools\examples\g`（分�?`g1` �?`g2`�? 
> - 任务标识示例：`doc-demo-20241103`  
> - 当接口出错时，可查看 `C:\Users\TyMos\.migratediff\logs\migratediff.log`

---

## 1. 仓库配置接口（RepoController�?
### 1.1 列出仓库配置
- **方法**：`GET /api/repos`
- **说明**：当前实现返回空数组，占位用于未来持久化配置�?- **示例**
  ```powershell
  Invoke-WebRequest -Method Get -Uri 'http://localhost:8081/api/repos' |
    Select-Object -Expand Content
  ```
  ```json
  []
  ```

### 1.2 新增/回显仓库配置
- **方法**：`POST /api/repos`
- **请求�?*
  ```json
  {
    "repoPath": {"absolutePath": "...", "type": "ORACLE | GAUSS"},
    "branchFrom": {"name": "..."},
    "branchTo": {"name": "..."},
    "deltaType": "DELTA_O | DELTA_G",
    "fetchIfMissing": true,
    "includeWorkingTree": false,
    "remoteName": "origin"
  }
  ```
- **示例（Oracle 仓库�?*
  ```powershell
  $body = '{
    "repoPath": {"absolutePath": "D:\\\Coding\\\code-compare-tools\\\examples\\\o","type": "ORACLE"},
    "branchFrom": {"name": "o1"},
    "branchTo": {"name": "o2"},
    "deltaType": "DELTA_O",
    "fetchIfMissing": true,
    "includeWorkingTree": false,
    "remoteName": "origin"
  }'
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/repos' `
    -Body $body -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"repoPath":{"absolutePath":"D:\\Coding\\code-compare-tools\\examples\\o","type":"ORACLE"},"branchFrom":{"name":"o1","commitId":null},"branchTo":{"name":"o2","commitId":null},"deltaType":"DELTA_O","fetchIfMissing":true,"includeWorkingTree":false,"remoteName":"origin"}
  ```

### 1.3 列出仓库分支
- **方法**：`POST /api/repos/branches`
- **说明**：根据配置扫描仓库，返回所有本�?远程分支�?- **示例**
  ```powershell
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/repos/branches' `
    -Body $body -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  [{"name":"o1","commitId":"01ffc0af71f9557495f216d15939544fb71f0cbd"},{"name":"o2","commitId":"6305861bed1385ac1b9b424088593f791919305a"}]
  ```

---

## 2. 扫描接口（ScanController�?
所有扫描请求都要求传入 Oracle �?Gauss 两侧的差异配置，结构为：
```json
{
## 2. ɨ��ӿڣ�ScanController��
����ɨ������֧�������÷���
1. ֱ�Ӵ��� Oracle �� Gauss �������������ã���ɰ���ݣ���
2. ָ�� `presetName`���ɺ�˼���Ԥ�裬��ͨ�� `oracle`��`gauss` ���Ǹ����ֶΣ���Ϊ�գ���

��������ṹ��
```json
{
  "taskId": "��ѡ�����ʶ",
  "persistResult": true,
  "presetName": "default-og",
  "oracle": {
    "branchTo": "o3"
  },
  "gauss": {}
}
```
> ��δָ�� `presetName`��������� `oracle` �� `gauss` ����д `repoPath`��`branchFrom`��`branchTo`��

### 2.0 Ԥ���б�
- **����**��`GET /api/scan/presets`
- **˵��**������ `application.properties` �����õ�Ԥ���б�������ǰ����Ⱦѡ�
- **ʾ��**
  ```powershell
  Invoke-WebRequest -Method Get -Uri 'http://localhost:8081/api/scan/presets' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"","data":[{"name":"default-og","source":{"code":"o-g","path":"D:\\Coding\\code-compare-tools\\examples\\o","branchFrom":"refs/heads/o1","branchTo":"refs/heads/o2","deltaType":"DELTA_O","includeWorkingTree":false,"fetchIfMissing":true,"remoteName":"origin"},"target":{"code":"g","path":"D:\\Coding\\code-compare-tools\\examples\\g","branchFrom":"refs/heads/g1","branchTo":"refs/heads/g2","deltaType":"DELTA_G","includeWorkingTree":false,"fetchIfMissing":true,"remoteName":"origin"}}]}
  ```

### 2.1 ����ɨ��
- **����**��`POST /api/scan`
- **˵��**�������� `includeWorkingTree=false` �ұ�����δ�ύ�Ķ�ʱ������ɨ����ܷ��ؿվ���
- **ʾ��**
  ```powershell
  $scan = '{
    "taskId": "doc-demo-20241103",
    "persistResult": true,
    "presetName": "default-og"
  }'
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/scan' `
    -Body $scan -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"","data":{"taskId":"doc-demo-20241103","summary":{"totalFiles":0,"oracleOnly":0,"gaussOnly":0,"matched":0,"consistencyRate":1.0},"diffMatrix":[]}}
  ```

### 2.2 ȫ��ɨ��
- **����**��`POST /api/scan/full`
- **˵��**�����ڷ�֧�����г����в����ļ�����������ѯ��Ǩ��ʹ�á�
- **ʾ��**
  ```powershell
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/scan/full' `
    -Body $scan -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"","data":{"taskId":"doc-demo-20241103","summary":{"totalFiles":17,"oracleOnly":10,"gaussOnly":3,"matched":0,"consistencyRate":0.0},"diffMatrix":[{"filePath":"docs/reporting-faq.md","oracleDelta":"+4/-4","gaussDelta":"+0/-0","coverage":0.0,"status":"oracle-only"}, ...]}}
  ```
  }
  ```
- **示例**
  ```powershell
  $detail = '{
    "taskId": "doc-demo-20241103",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java"
  }'
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/scan/detail' `
    -Body $detail -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"","data":{"taskId":"doc-demo-20241103","filePath":"src/main/java/com/example/migration/billing/SettlementProcessor.java","oracleDiff":{"before":"...","after":"..."},"gaussDiff":{"before":"...","after":"..."},"migrationDiff":"/** ... */","stats":{"oracleAdded":23,"oracleRemoved":23,"gaussAdded":24,"gaussRemoved":24},"coverage":0.0}}
  ```
  > 注意：原始模板包含全角字符，默认编码会在 PowerShell 输出中显示为 `???`，但内容已成功返回�?
---

## 3. 迁移执行接口（MigrationController�?
迁移操作依赖扫描缓存：必须先全量扫描，再查询明细/生成模板�?
### 3.1 生成迁移模板
- **方法**：`POST /api/migrate/generate`
- **请求�?*
  ```json
  {
    "taskId": "doc-demo-20241103",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
    "options": {
      "ignoreWhitespace": false,
      "ignoreComments": false
    }
  }
  ```
- **示例**
  ```powershell
  $migrate = '{
    "taskId": "doc-demo-20241103",
    "filePath": "src/main/java/com/example/migration/billing/SettlementProcessor.java",
    "options": {"ignoreWhitespace": false,"ignoreComments": false}
  }'
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/migrate/generate' `
    -Body $migrate -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"Migration diff generated","data":{"taskId":"doc-demo-20241103","filePath":"src/main/java/com/example/migration/billing/SettlementProcessor.java","migrationDiff":"/** ... */"}}
  ```

### 3.2 应用迁移模板
- **方法**：`POST /api/migrate/apply`
- **请求�?*（同上，`options` 可省略）
- **示例**
  ```powershell
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/migrate/apply' `
    -Body $detail -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"Migration applied","data":{"taskId":"doc-demo-20241103","filePath":"src/main/java/com/example/migration/billing/SettlementProcessor.java","logId":599640976123399328}}
  ```
  > `logId` 为系统随机生成，可与后续日志或外部系统对接�?
### 3.3 回退已应用迁�?- **方法**：`POST /api/migrate/revert`
- **说明**：根据之前记录的迁移任务 ID 回退最新一次应用�?- **示例**
  ```powershell
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/migrate/revert' `
    -Body $detail -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"status":"success","message":"Migration reverted","data":null}
  ```

---

## 4. 系统设置接口（SettingController�?
设置暂存于后端内存哈希表中（暂无持久化），可用于配置路径、开关等�?
### 4.1 查询设置
- **方法**：`GET /api/settings/{key}`
- **示例**
  ```powershell
  Invoke-WebRequest -Method Get -Uri 'http://localhost:8081/api/settings/migrate.logDir'
  ```
  - 初次访问返回 `HTTP 200` 且内容为空�?
### 4.2 更新设置
- **方法**：`POST /api/settings`
- **请求�?*
  ```json
  {
    "key": "migrate.logDir",
    "value": "D:\\temp\\logs",
    "description": "迁移日志目录"
  }
  ```
- **示例**
  ```powershell
  $setting = '{
    "key": "migrate.logDir",
    "value": "D:\\\temp\\\logs",
    "description": "迁移日志目录"
  }'
  Invoke-WebRequest -Method Post -Uri 'http://localhost:8081/api/settings' `
    -Body $setting -ContentType 'application/json' |
    Select-Object -Expand Content
  ```
  ```json
  {"key":"migrate.logDir","value":"D:\\temp\\logs","description":"??????"}
  ```
  再次查询即可得到同样的值；描述字段受编码影响显示为 `??????`，后续可通过显式字符集配置解决�?
---

## 5. 常见问题与排�?
- **接口验证失败**：检查示例仓库是否存在对应分支；若缺失，可执�?`git fetch origin o1:o1` 等命令补齐�?- **扫描结果为空**：确认是否执行了全量扫描；增量模式在无工作区改动时可能返回空列表�?- **迁移生成报错**：需先执行全量扫描并保证 `taskId` 与文件路径在扫描结果中存在�?- **中文显示为问�?*：当前响应默认编码为 UTF-8，但部分模板原文含有 double-byte 字符，在 PowerShell 控制台会出现 `???`。可在前端或调用方显式解码处理�?
---

## 6. 验证总结

- Repo、Scan、Migration、Setting 四类接口均已在示例仓库上验证通过�?- 典型流程：`POST /api/scan/full` �?`POST /api/scan/detail` �?`POST /api/migrate/generate` �?`POST /api/migrate/apply` �?`POST /api/migrate/revert`�?- 所有请求返�?`status: "success"`，且后端日志中仅保留一�?JSON 解析错误（测试时手动修正后未再出现）�?
如需前端接入，可直接复用本文档中的请�?响应结构并组�?`taskId` 与文件路径实现导航�?
