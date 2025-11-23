<template>
  <div class="file-diff-page">
    <el-row :gutter="16">
      <el-col :span="24">
        <el-card>
          <div class="file-diff-page__info">
            <div>
              <div class="file-diff-page__path">{{ currentFile.filePath || '请选择文件' }}</div>
              <div class="file-diff-page__meta">
                <span>模块：{{ currentFile.module || '--' }}</span>
                <span>覆盖率：{{ formatPercent(currentFile.coverage) }}</span>
                <span>ΔO：+{{ currentFile.stats.oracleAdded }}/-{{ currentFile.stats.oracleRemoved }}</span>
                <span>ΔG：+{{ currentFile.stats.gaussAdded }}/-{{ currentFile.stats.gaussRemoved }}</span>
              </div>
            </div>
            <el-button-group>
              <el-button size="mini" @click="navigatePrev" :disabled="!hasPrev">
                上一文件
              </el-button>
              <el-button size="mini" @click="navigateNext" :disabled="!hasNext">
                下一文件
              </el-button>
            </el-button-group>
          </div>
          
          <!-- 提交历史组件 -->
          <div 
            v-if="shouldShowCommitHistory" 
            class="commit-history-container"
          >
            <div class="commit-history-header" @click="toggleCommitHistory">
              <div class="commit-history-title">
                <i class="el-icon-time"></i>
                <span>OG 提交历史</span>
                <span v-if="commitHistoryStats.total > 0" class="commit-count">
                  {{ commitHistoryStats.total }}次
                </span>
              </div>
              <span class="expand-btn">
                {{ commitHistoryExpanded ? '收起' : '展开' }}
                <i :class="commitHistoryExpanded ? 'el-icon-arrow-up' : 'el-icon-arrow-down'"></i>
              </span>
            </div>
            
            <el-collapse-transition>
              <div v-show="commitHistoryExpanded" class="commit-history-content">
                <div v-loading="loadingCommitHistory" class="commit-timeline">
                  <div 
                    v-for="(commit, index) in mergedCommitHistory" 
                    :key="commit.hash + index"
                    class="timeline-item"
                    @click="openCommitInBrowser(commit)"
                  >
                    <div class="timeline-dot" :class="commit.type"></div>
                    <div class="commit-item">
                      <div class="commit-header">
                        <span class="commit-type" :class="commit.type">
                          {{ commit.type === 'oracle' ? '🟠 O:' : '🟢 G:' }}
                        </span>
                        <a 
                          :href="commit.url" 
                          target="_blank" 
                          class="commit-hash"
                          @click.stop
                        >
                          {{ commit.shortHash }}
                        </a>
                        <span class="commit-time">{{ formatRelativeTime(commit.time) }}</span>
                      </div>
                      <div class="commit-author">
                        <span class="author-name">{{ commit.author }}</span>
                      </div>
                      <div class="commit-message">{{ commit.message }}</div>
                      <div class="commit-stats" v-if="commit.stats">
                        <span class="stat-item stat-added">+{{ commit.stats.added || 0 }}</span>
                        <span class="stat-item stat-removed">-{{ commit.stats.removed || 0 }}</span>
                      </div>
                    </div>
                  </div>
                  
                  <!-- 空状态 -->
                  <div v-if="mergedCommitHistory.length === 0 && !loadingCommitHistory" class="empty-state">
                    <i class="el-icon-document-remove"></i>
                    <div>该文件暂无提交历史</div>
                  </div>
                </div>
              </div>
            </el-collapse-transition>
          </div>
          
          <diff-toolbar
            :value="diffMode"
            :loading="migrating"
            :disable-generate="!canOperate"
            :disable-apply="!canApply"
            :disable-revert="!canApply"
            :default-options="viewerOptions"
            @input="handleModeChange"
            @options-change="handleOptionsChange"
            @generate="handleGenerate"
            @apply="handleApply"
            @revert="handleRevert"
          />
          <file-diff-viewer
            :file-path="currentFile.filePath"
            :mode="diffMode"
            :oracle-diff="currentFile.oracleDiff"
            :gauss-diff="currentFile.gaussDiff"
            :migration-diff="currentFile.migrationDiff"
            :options="viewerOptions"
          />
        </el-card>
      </el-col>
    </el-row>
    <el-skeleton v-if="loadingDetail && !currentFile.filePath" :rows="6" animated />
  </div>
</template>

<script>
import { mapState, mapActions, mapMutations } from 'vuex';
import DiffToolbar from '../components/DiffToolbar.vue';
import FileDiffViewer from '../components/FileDiffViewer.vue';
import commitApi from '../api/commit';

export default {
  name: 'FileDiffPage',
  components: {
    DiffToolbar,
    FileDiffViewer,
  },
  props: {
    taskId: {
      type: String,
      default: '',
    },
    filePath: {
      type: String,
      default: '',
    },
    oracleDelta: {
      type: String,
      default: '',
    },
    gaussDelta: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      initialized: false,
      viewerOptions: {
        inlineView: false,
        ignoreWhitespace: false,
        collapseUnchanged: false,
      },
      // 提交历史相关状态
      commitHistoryExpanded: false,
      loadingCommitHistory: false,
      commitHistoryCache: new Map(), // 性能优化：缓存提交历史
      oracleCommits: [],
      gaussCommits: [],
    };
  },
  computed: {
    ...mapState('diff', {
      storeTaskId: 'taskId',
      currentFile: 'currentFile',
      diffMatrix: 'diffMatrix',
      loadingDetail: 'loadingDetail',
      loadingMatrix: 'loadingMatrix',
      diffMode: 'diffMode',
      migrating: 'migrating',
    }),
    currentIndex() {
      return this.diffMatrix.findIndex((item) => item.filePath === this.currentFile.filePath);
    },
    hasPrev() {
      return this.currentIndex > 0;
    },
    hasNext() {
      return this.currentIndex >= 0 && this.currentIndex < this.diffMatrix.length - 1;
    },
    canOperate() {
      return Boolean(this.currentFile.filePath);
    },
    canApply() {
      return Boolean(this.currentFile.migrationDiff);
    },
    // 是否显示提交历史组件
    shouldShowCommitHistory() {
      return Boolean(this.currentFile.filePath);
    },
    // 提交历史统计
    commitHistoryStats() {
      return {
        total: this.oracleCommits.length + this.gaussCommits.length,
        oracle: this.oracleCommits.length,
        gauss: this.gaussCommits.length,
      };
    },
    // 合并的提交历史（按时间排序）
    mergedCommitHistory() {
      const allCommits = [
        ...this.oracleCommits.map(commit => ({ ...commit, type: 'oracle' })),
        ...this.gaussCommits.map(commit => ({ ...commit, type: 'gauss' })),
      ];
      
      return allCommits.sort((a, b) => new Date(b.time) - new Date(a.time));
    },
  },
  watch: {
    taskId: {
      immediate: true,
      handler(next) {
        if (next && next !== this.storeTaskId) {
          this.setTaskId(next);
        }
      },
    },
    filePath: {
      immediate: true,
      handler() {
        this.ensureCurrentFile();
      },
    },
    diffMatrix(newValue, oldValue) {
      if (!this.initialized && newValue !== oldValue) {
        this.ensureCurrentFile();
      }
    },
    // 监听当前文件变化，重置提交历史状态
    'currentFile.filePath': {
      immediate: true,
      handler(newPath, oldPath) {
        if (newPath && newPath !== oldPath) {
          this.commitHistoryExpanded = false;
          this.loadCommitHistory();
        }
      },
    },
  },
  created() {
    if (this.taskId && this.taskId !== this.storeTaskId) {
      this.setTaskId(this.taskId);
    }
    this.ensureCurrentFile(true);
  },
  methods: {
    ...mapActions('diff', ['fetchDetail', 'generateMigration', 'applyMigration', 'revertMigration', 'fetchCommitHistory']),
    ...mapMutations('diff', ['setDiffMode', 'setTaskId']),
    async ensureCurrentFile(force = false) {
      if (this.loadingMatrix && !force) {
        return;
      }
      if (!this.storeTaskId || !this.diffMatrix.length) {
        if (!this.diffMatrix.length && !force) {
          this.$message.info('暂无缓存结果，请先在仪表盘执行全量扫描');
        }
        this.initialized = false;
        return;
      }
      const fallback = this.diffMatrix.length > 0 ? this.diffMatrix[0].filePath : '';
      const targetPath = this.filePath || this.currentFile.filePath || fallback;
      if (!targetPath) {
        return;
      }
      try {
        await this.fetchDetail({ filePath: targetPath });
        this.initialized = true;
      } catch (error) {
        this.$message.error(error.message || '加载文件详情失败');
      }
    },
    handleModeChange(mode) {
      this.setDiffMode(mode);
    },
    handleOptionsChange(options) {
      this.viewerOptions = { ...options };
    },
    buildMigrationOptions() {
      return {
        ignoreWhitespace: this.viewerOptions.ignoreWhitespace,
        collapseUnchanged: this.viewerOptions.collapseUnchanged,
      };
    },
    async handleGenerate() {
      if (!this.currentFile.filePath) {
        this.$message.warning('请选择需要迁移的文件');
        return;
      }
      try {
        const result = await this.generateMigration({
          filePath: this.currentFile.filePath,
          options: this.buildMigrationOptions(),
        });
        this.$message.success(result?.message || '迁移结果已生成');
      } catch (error) {
        this.$message.error(error.message || '生成失败');
      }
    },
    async handleApply() {
      if (!this.currentFile.filePath) {
        this.$message.warning('请选择需要应用的文件');
        return;
      }
      try {
        const result = await this.applyMigration({ filePath: this.currentFile.filePath });
        this.$message.success(result?.message || '迁移结果已应用');
      } catch (error) {
        this.$message.error(error.message || '应用失败');
      }
    },
    async handleRevert() {
      if (!this.currentFile.filePath) {
        this.$message.warning('请选择需要撤销的文件');
        return;
      }
      try {
        const result = await this.revertMigration({ filePath: this.currentFile.filePath });
        this.$message.success(result?.message || '迁移结果已撤销');
      } catch (error) {
        this.$message.error(error.message || '撤销失败');
      }
    },
    async navigatePrev() {
      if (!this.hasPrev) {
        return;
      }
      const prev = this.diffMatrix[this.currentIndex - 1];
      if (!prev) {
        return;
      }
      await this.fetchDetail({ filePath: prev.filePath });
      this.updateRoute(prev);
    },
    async navigateNext() {
      if (!this.hasNext) {
        return;
      }
      const next = this.diffMatrix[this.currentIndex + 1];
      if (!next) {
        return;
      }
      await this.fetchDetail({ filePath: next.filePath });
      this.updateRoute(next);
    },
    updateRoute(row) {
      this.$router.replace({
        name: 'FileDiff',
        query: {
          taskId: this.storeTaskId,
          filePath: row.filePath,
          oracleDelta: row.oracleDelta,
          gaussDelta: row.gaussDelta,
        },
      });
    },
    formatPercent(value) {
      if (value === undefined || value === null || value === '') {
        return '--';
      }
      const numeric = Number(value);
      if (Number.isNaN(numeric)) {
        return '--';
      }
      return `${(numeric * 100).toFixed(1)}%`;
    },
    
    // 提交历史相关方法
    toggleCommitHistory() {
      this.commitHistoryExpanded = !this.commitHistoryExpanded;
      
      // 性能优化：只在首次展开时加载提交历史
      if (this.commitHistoryExpanded && this.oracleCommits.length === 0 && this.gaussCommits.length === 0) {
        this.loadCommitHistory();
      }
    },
    
    async loadCommitHistory() {
      if (!this.currentFile.filePath) {
        return;
      }
      
      // 性能优化：检查缓存
      const cacheKey = `${this.storeTaskId}:${this.currentFile.filePath}`;
      if (this.commitHistoryCache.has(cacheKey)) {
        const cached = this.commitHistoryCache.get(cacheKey);
        this.oracleCommits = cached.oracle || [];
        this.gaussCommits = cached.gauss || [];
        return;
      }
      
      this.loadingCommitHistory = true;
      
      try {
        // 模拟API调用 - 实际应该调用后端API
        await this.fetchCommitHistoryFromAPI();
        
        // 缓存结果
        this.commitHistoryCache.set(cacheKey, {
          oracle: this.oracleCommits,
          gauss: this.gaussCommits,
        });
        
      } catch (error) {
        console.warn('加载提交历史失败:', error);
        this.$message.warning('加载提交历史失败，请稍后重试');
      } finally {
        this.loadingCommitHistory = false;
      }
    },
    
    async fetchCommitHistoryFromAPI() {
      try {
        // 调用真实的API获取提交历史
        const response = await this.fetchCommitHistory({
          taskId: this.storeTaskId,
          filePath: this.currentFile.filePath
        });
        
        // 使用API返回的数据
        this.oracleCommits = response.oracleCommits || [];
        this.gaussCommits = response.gaussCommits || [];
        
      } catch (error) {
        console.warn('获取提交历史失败:', error);
        // 失败时设置为空数组
        this.oracleCommits = [];
        this.gaussCommits = [];
        throw error;
      }
    },
    
    openCommitInBrowser(commit) {
      if (commit.url) {
        window.open(commit.url, '_blank');
      }
    },
    
    formatRelativeTime(timestamp) {
      if (!timestamp) return '';
      
      const now = new Date();
      const time = new Date(timestamp);
      const diff = now - time;
      
      const minute = 60 * 1000;
      const hour = 60 * minute;
      const day = 24 * hour;
      const month = 30 * day;
      const year = 365 * day;
      
      if (diff < minute) {
        return '刚刚';
      } else if (diff < hour) {
        return `${Math.floor(diff / minute)}分钟前`;
      } else if (diff < day) {
        return `${Math.floor(diff / hour)}小时前`;
      } else if (diff < month) {
        return `${Math.floor(diff / day)}天前`;
      } else if (diff < year) {
        return `${Math.floor(diff / month)}个月前`;
      } else {
        return `${Math.floor(diff / year)}年前`;
      }
    },
  },
};
</script>

<style scoped>
.file-diff-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.file-diff-page__info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.file-diff-page__path {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 8px;
}

.file-diff-page__meta {
  display: flex;
  gap: 16px;
  color: #909399;
  font-size: 13px;
}

/* 提交历史样式 */
.commit-history-container {
  margin: 12px 0;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}

.commit-history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.3s;
}

.commit-history-header:hover {
  background: #ecf5ff;
}

.commit-history-title {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #303133;
}

.commit-history-title i {
  color: #409eff;
}

.commit-count {
  background: #409eff;
  color: white;
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: normal;
}

.expand-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.commit-history-content {
  background: white;
}

.commit-timeline {
  position: relative;
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.commit-timeline::before {
  content: '';
  position: absolute;
  left: 16px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e4e7ed;
}

.timeline-item {
  position: relative;
  margin-bottom: 16px;
  padding-left: 32px;
}

.timeline-item:last-child {
  margin-bottom: 0;
}

.timeline-dot {
  position: absolute;
  left: 10px;
  top: 8px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: white;
  border: 2px solid #409eff;
  z-index: 1;
}

.timeline-dot.oracle {
  border-color: #e6a23c;
  background: #fdf6ec;
}

.timeline-dot.gauss {
  border-color: #67c23a;
  background: #f0f9eb;
}

.commit-item {
  background: white;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.3s;
}

.commit-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
  transform: translateY(-1px);
}

.commit-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.commit-type {
  font-weight: 600;
  font-size: 12px;
  min-width: 35px;
}

.commit-type.oracle {
  color: #e6a23c;
}

.commit-type.gauss {
  color: #67c23a;
}

.commit-hash {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
  text-decoration: none;
}

.commit-hash:hover {
  text-decoration: underline;
}

.commit-time {
  font-size: 11px;
  color: #909399;
  margin-left: auto;
}

.commit-author {
  margin-bottom: 6px;
}

.author-name {
  font-size: 13px;
  color: #303133;
  font-weight: 500;
}

.commit-message {
  font-size: 13px;
  color: #606266;
  line-height: 1.4;
  word-break: break-word;
  margin-bottom: 8px;
}

.commit-stats {
  display: flex;
  gap: 12px;
  font-size: 11px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 2px;
}

.stat-added {
  color: #67c23a;
}

.stat-removed {
  color: #f56c6c;
}

.empty-state {
  text-align: center;
  padding: 40px 20px;
  color: #909399;
  font-size: 13px;
}

.empty-state i {
  font-size: 32px;
  margin-bottom: 12px;
  opacity: 0.5;
  display: block;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .file-diff-page__meta {
    flex-wrap: wrap;
    gap: 8px;
  }
  
  .commit-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  
  .commit-time {
    margin-left: 0;
    align-self: flex-start;
  }
  
  .commit-stats {
    flex-direction: column;
    gap: 4px;
  }
  
  .timeline-item {
    padding-left: 24px;
  }
  
  .timeline-dot {
    left: 8px;
    width: 10px;
    height: 10px;
  }
  
  .commit-timeline::before {
    left: 12px;
  }
}
</style>
