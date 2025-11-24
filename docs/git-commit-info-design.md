# 文件对比页面Git提交信息显示功能设计方案

## 1. 需求分析

在现有的文件对比页面（FileDiffPage.vue）中新增Git提交信息显示，让用户能够：
- 查看文件在Oracle和Gauss仓库中的最新提交信息
- 了解文件的变更历史和作者信息
- 提供更上下文化的文件对比体验

## 2. 现有架构分析

### 前端架构
- **FileDiffPage.vue**: 主要的文件对比页面
- **DiffDetailResponseDTO**: 当前返回的数据结构包含taskId、filePath、oracleDiff、gaussDiff、migrationDiff、stats、coverage
- **Store diff.js**: 管理状态和API调用

### 后端架构
- **GitRepositoryHelper**: 提供Git操作工具方法
- **SnapshotLocator**: 处理Git快照和提交信息
- **RevCommit处理**: 已有RevCommit相关代码在SnapshotLocator中
- **DTO结构**: 完善的DTO体系用于数据传输

## 3. 设计方案

### 3.1 后端设计方案

#### 3.1.1 新增Git提交信息DTO

```java
// GitCommitInfoDTO.java
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GitCommitInfoDTO {
    private String commitHash;           // 提交哈希值
    private String shortHash;           // 短哈希值
    private String authorName;          // 作者姓名
    private String authorEmail;         // 作者邮箱
    private Instant commitTime;        // 提交时间
    private String message;            // 提交信息
    private String branch;             // 分支名
}

// FileCommitInfoDTO.java  
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileCommitInfoDTO {
    private String filePath;
    private GitCommitInfoDTO oracleCommit;    // Oracle仓库的最新提交
    private GitCommitInfoDTO gaussCommit;     // Gauss仓库的最新提交
    private boolean hasOracleChanges;          // Oracle是否有变更
    private boolean hasGaussChanges;           // Gauss是否有变更
}
```

#### 3.1.2 扩展现有DTO

```java
// 在DiffDetailResponseDTO中新增字段
@Data
public class DiffDetailResponseDTO {
    // ... 现有字段
    private FileCommitInfoDTO commitInfo;    // 新增：文件提交信息
}
```

#### 3.1.3 新增Git服务层

```java
// GitCommitInfoService.java
@Service
public class GitCommitInfoService {
    
    private final GitRepositoryHelper gitRepositoryHelper;
    
    public FileCommitInfoDTO getFileCommitInfo(String taskId, String filePath) {
        // 获取任务配置
        // 查询Oracle和Gauss仓库中该文件的最新提交信息
        // 构建返回的DTO
    }
    
    private GitCommitInfoDTO getLatestCommitForFile(RepoConfig repoConfig, String filePath, String branch) {
        // 使用RevWalk遍历提交历史
        // 找到最后一次修改该文件的提交
        // 提取提交信息
    }
}
```

#### 3.1.4 扩展现有API

```java
// 在现有的文件详情API中集成提交信息
@GetMapping("/detail")
public ApiResponse<DiffDetailResponseDTO> getFileDetail(
    @RequestParam String taskId,
    @RequestParam String filePath
) {
    // ... 现有逻辑
    DiffDetailResponseDTO response = buildDiffDetail(taskId, filePath);
    
    // 新增：获取Git提交信息
    FileCommitInfoDTO commitInfo = gitCommitInfoService.getFileCommitInfo(taskId, filePath);
    response.setCommitInfo(commitInfo);
    
    return ApiResponse.success(response);
}
```

### 3.2 前端设计方案

#### 3.2.1 扩展Store状态

```javascript
// store/diff.js 中扩展 currentFile 结构
const defaultCurrentFile = () => ({
  // ... 现有字段
  commitInfo: {
    filePath: '',
    oracleCommit: null,
    gaussCommit: null,
    hasOracleChanges: false,
    hasGaussChanges: false,
  }
});
```

#### 3.2.2 新增Git提交信息组件

```vue
<!-- components/GitCommitInfo.vue -->
<template>
  <div class="git-commit-info">
    <div class="commit-info__header">
      <i class="el-icon-info"></i>
      <span>Git 提交信息</span>
      <el-button 
        size="mini" 
        type="text" 
        @click="toggleExpanded"
        class="expand-btn"
      >
        {{ expanded ? '收起' : '展开' }}
        <i :class="expanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"></i>
      </el-button>
    </div>
    
    <el-collapse-transition>
      <div v-show="expanded" class="commit-info__content">
        <!-- Oracle提交信息 -->
        <div v-if="commitInfo.oracleCommit" class="commit-section">
          <div class="commit-section__header oracle">
            <i class="el-icon-document"></i>
            <span>Oracle 最新提交</span>
            <el-tag 
              v-if="commitInfo.hasOracleChanges" 
              type="warning" 
              size="mini"
            >
              有变更
            </el-tag>
          </div>
          <div class="commit-details">
            <div class="commit-row">
              <span class="label">提交:</span>
              <el-link 
                :href="getCommitUrl(commitInfo.oracleCommit)" 
                target="_blank"
                type="primary"
                class="commit-hash"
              >
                {{ commitInfo.oracleCommit.shortHash }}
              </el-link>
            </div>
            <div class="commit-row">
              <span class="label">作者:</span>
              <span>{{ commitInfo.oracleCommit.authorName }}</span>
            </div>
            <div class="commit-row">
              <span class="label">时间:</span>
              <span>{{ formatTime(commitInfo.oracleCommit.commitTime) }}</span>
            </div>
            <div class="commit-row">
              <span class="label">信息:</span>
              <span class="commit-message">{{ commitInfo.oracleCommit.message }}</span>
            </div>
          </div>
        </div>

        <!-- Gauss提交信息 -->
        <div v-if="commitInfo.gaussCommit" class="commit-section">
          <div class="commit-section__header gauss">
            <i class="el-icon-document"></i>
            <span>Gauss 最新提交</span>
            <el-tag 
              v-if="commitInfo.hasGaussChanges" 
              type="success" 
              size="mini"
            >
              有变更
            </el-tag>
          </div>
          <div class="commit-details">
            <div class="commit-row">
              <span class="label">提交:</span>
              <el-link 
                :href="getCommitUrl(commitInfo.gaussCommit)" 
                target="_blank"
                type="primary"
                class="commit-hash"
              >
                {{ commitInfo.gaussCommit.shortHash }}
              </el-link>
            </div>
            <div class="commit-row">
              <span class="label">作者:</span>
              <span>{{ commitInfo.gaussCommit.authorName }}</span>
            </div>
            <div class="commit-row">
              <span class="label">时间:</span>
              <span>{{ formatTime(commitInfo.gaussCommit.commitTime) }}</span>
            </div>
            <div class="commit-row">
              <span class="label">信息:</span>
              <span class="commit-message">{{ commitInfo.gaussCommit.message }}</span>
            </div>
          </div>
        </div>
      </div>
    </el-collapse-transition>
  </div>
</template>

<script>
export default {
  name: 'GitCommitInfo',
  props: {
    commitInfo: {
      type: Object,
      default: () => ({})
    }
  },
  data() {
    return {
      expanded: false
    }
  },
  methods: {
    toggleExpanded() {
      this.expanded = !this.expanded
    },
    formatTime(timestamp) {
      if (!timestamp) return '--'
      return new Date(timestamp).toLocaleString()
    },
    getCommitUrl(commit) {
      // 根据仓库配置生成Git Web URL
      // 这里需要根据实际的Git服务器配置
      if (!commit || !commit.commitHash) return '#'
      return `#/commit/${commit.commitHash}`
    }
  }
}
</script>

<style scoped>
.git-commit-info {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  margin: 12px 0;
}

.commit-info__header {
  display: flex;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 500;
}

.expand-btn {
  margin-left: auto;
}

.commit-info__content {
  padding: 16px;
}

.commit-section {
  margin-bottom: 16px;
}

.commit-section:last-child {
  margin-bottom: 0;
}

.commit-section__header {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
}

.commit-section__header.oracle {
  color: #e6a23c;
}

.commit-section__header.gauss {
  color: #67c23a;
}

.commit-details {
  padding-left: 20px;
}

.commit-row {
  display: flex;
  margin-bottom: 6px;
  font-size: 13px;
}

.commit-row .label {
  color: #909399;
  min-width: 50px;
  margin-right: 8px;
}

.commit-hash {
  font-family: 'Courier New', monospace;
  font-size: 12px;
}

.commit-message {
  color: #606266;
  flex: 1;
  word-break: break-word;
}
</style>
```

#### 3.2.3 集成到FileDiffPage

```vue
<!-- FileDiffPage.vue 中集成 -->
<template>
  <div class="file-diff-page">
    <el-row :gutter="16">
      <el-col :span="24">
        <el-card>
          <div class="file-diff-page__info">
            <!-- 现有信息展示 -->
          </div>
          
          <!-- 新增：Git提交信息组件 -->
          <git-commit-info 
            v-if="currentFile.commitInfo && (currentFile.commitInfo.oracleCommit || currentFile.commitInfo.gaussCommit)"
            :commit-info="currentFile.commitInfo"
          />
          
          <diff-toolbar ... />
          <file-diff-viewer ... />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import GitCommitInfo from '../components/GitCommitInfo.vue'

export default {
  components: {
    // ... 现有组件
    GitCommitInfo,
  },
  // ... 其他逻辑
}
</script>
```

## 4. 线框图设计

### 4.1 折叠状态
```
┌─────────────────────────────────────────────────────────────┐
│ 📄 src/main/java/com/example/Example.java                    │
│ 模块：example-service  覆盖率：85.2%  ΔO：+12/-3  ΔG：+15/-5 │
│ [上一文件] [下一文件]                                       │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ℹ️ Git 提交信息                    [展开 ▼]           │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [对比模式] [生成] [应用] [撤销]                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │                   文件差异显示区域                       │ │
│ │                                                         │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 展开状态
```
┌─────────────────────────────────────────────────────────────┐
│ 📄 src/main/java/com/example/Example.java                    │
│ 模块：example-service  覆盖率：85.2%  ΔO：+12/-3  ΔG：+15/-5 │
│ [上一文件] [下一文件]                                       │
│                                                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ ℹ️ Git 提交信息                    [收起 ▲]           │ │
│ ├─────────────────────────────────────────────────────────┤ │
│ │ 🟠 Oracle 最新提交                    [有变更]          │ │
│ │   提交: a1b2c3d                                        │ │
│ │   作者: 张三                                           │ │
│ │   时间: 2024/01/15 14:30:22                            │ │
│ │   信息: 修复Example类的空指针异常                        │ │
│ │                                                         │ │
│ │ 🟢 Gauss 最新提交                    [有变更]          │ │
│ │   提交: f4e5d6c7                                        │ │
│ │   作者: 李四                                           │ │
│ │   时间: 2024/01/16 09:15:45                            │ │
│ │   信息: 迁移Example类到Gauss数据库适配                  │ │
│ └─────────────────────────────────────────────────────────┘ │
│                                                             │
│ [对比模式] [生成] [应用] [撤销]                             │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │                   文件差异显示区域                       │ │
│ │                                                         │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 5. 实现建议

### 5.1 开发优先级
1. **高优先级**: 后端Git提交信息查询服务
2. **中优先级**: 前端GitCommitInfo组件开发
3. **低优先级**: Git Web URL集成和高级功能

### 5.2 性能考虑
- **缓存策略**: Git提交信息相对稳定，可以适当缓存
- **异步加载**: 提交信息可以异步加载，不阻塞主要功能
- **按需查询**: 只在展开时才查询详细信息

### 5.3 扩展性考虑
- **URL配置**: 支持不同Git服务器的URL格式配置
- **多分支支持**: 未来可以扩展显示多个分支的提交信息
- **历史记录**: 可以进一步扩展显示完整的提交历史

### 5.4 错误处理
- **仓库不存在**: 显示友好的错误提示
- **文件未找到**: 显示文件在某个仓库中不存在
- **权限问题**: 处理Git仓库访问权限问题

## 6. 测试策略

### 6.1 单元测试
- GitCommitInfoService的查询逻辑
- GitCommitInfo组件的渲染和交互

### 6.2 集成测试
- API接口返回正确的提交信息
- 前后端数据流转正确

### 6.3 边界测试
- 大文件的处理性能
- 特殊字符的显示
- 网络异常的处理
