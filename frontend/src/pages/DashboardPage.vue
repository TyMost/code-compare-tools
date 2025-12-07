<template>
  <div class="dashboard-page">
    <!-- 简化的工具栏 -->
    <div class="dashboard-page__toolbar">
      <div class="dashboard-page__toolbar-group">
        <el-select
          v-model="currentRepoId"
          placeholder="选择仓库"
          size="mini"
          class="dashboard-page__repo-select"
          :loading="isInitializing"
          @change="handleRepoChange"
        >
          <el-option
            v-for="repo in availableRepos"
            :key="repo.id"
            :label="repo.name"
            :value="repo.id"
          >
            <div class="repo-option">
              <span class="repo-option__name">{{ repo.name }}</span>
              <span v-if="getRepoCacheStatus(repo.id)" class="repo-option__cache">
                <i class="el-icon-circle-check"></i>
                {{ formatCacheTime(getRepoCacheStatus(repo.id).cachedAt) }}
              </span>
              <span v-else class="repo-option__no-cache">
                <i class="el-icon-time"></i>
                需要扫描
              </span>
            </div>
          </el-option>
        </el-select>
        
        <!-- 下拉菜单式刷新按钮 -->
        <el-dropdown @command="handleRefreshCommand" :disabled="!currentRepoId">
          <el-button 
            size="mini" 
            :loading="isScanning || batchRefreshing"
            :disabled="!currentRepoId"
          >
            🔄 {{ isScanning || batchRefreshing ? '刷新中...' : '刷新' }}
            <i class="el-icon-arrow-down el-icon--right"></i>
          </el-button>
          <el-dropdown-menu slot="dropdown">
            <el-dropdown-item command="current" :disabled="!currentRepoId">
              <i class="el-icon-refresh"></i>
              刷新当前仓库
            </el-dropdown-item>
            <el-dropdown-item command="all" divided>
              <i class="el-icon-refresh"></i>
              刷新所有仓库
            </el-dropdown-item>
          </el-dropdown-menu>
        </el-dropdown>

      <el-button
          size="mini"
          type="info"
          plain
          @click="openMultiExportDialog"
          :disabled="!hasData"
        >
          导出报表
        </el-button>
        
        <!-- 新增：添加配置按钮 -->
        <el-button
          size="mini"
          type="primary"
          plain
          @click="showAddConfigDialog"
        >
          ➕ 添加配置
        </el-button>
      </div>

      <!-- 简化的状态信息 -->
      <div v-if="currentRepoInfo" class="dashboard-page__repo-info">
        <span class="repo-info__name">{{ currentRepoInfo.name }}</span>
        <span class="repo-info__description">{{ currentRepoInfo.description }}</span>
        <span v-if="lastScanTime" class="repo-info__scan-time">
          最后扫描: {{ formatDateTime(lastScanTime) }}
        </span>
      </div>
    </div>

    <!-- 初始化状态 -->
    <el-alert
      v-if="isInitializing"
      title="正在初始化仓库配置..."
      type="info"
      :closable="false"
      class="dashboard-page__hint"
    />

    <!-- 无数据提示 -->
    <el-alert
      v-else-if="!hasData && !isScanning && !batchRefreshing"
      title="暂无数据，请选择仓库或点击刷新"
      type="info"
      :closable="false"
      class="dashboard-page__hint"
    />

    <!-- 批量刷新进度 -->
    <el-alert
      v-if="batchRefreshing"
      title="正在批量刷新仓库..."
      type="info"
      :closable="false"
      class="dashboard-page__batch-progress"
    >
      <div class="batch-progress-content">
        <div class="batch-progress-bar">
          <el-progress 
            :percentage="batchProgressPercentage" 
          />
        </div>
        <div class="batch-progress-text">
          {{ batchRefreshProgress.currentRepo }} ({{ batchRefreshProgress.current }}/{{ batchRefreshProgress.total }})
        </div>
      </div>
      
      <!-- 错误信息 -->
      <div v-if="batchRefreshProgress.errors.length > 0" class="batch-errors">
        <div class="error-title">刷新失败：</div>
        <div class="error-list">
          <div 
            v-for="error in batchRefreshProgress.errors" 
            :key="error.repoId"
            class="error-item"
          >
            <strong>{{ error.repo }}:</strong> {{ error.error }}
          </div>
        </div>
      </div>
    </el-alert>

    <!-- 统计概览 -->
    <el-row v-if="hasData" :gutter="16" class="dashboard-page__summary">
      <el-col :span="6">
        <el-card>
          <div class="dashboard-page__metric">
            <span class="dashboard-page__metric-label">总文件数</span>
            <span class="dashboard-page__metric-value">{{ summary.totalFiles }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="dashboard-page__metric">
            <span class="dashboard-page__metric-label">ΔO 独有</span>
            <span class="dashboard-page__metric-value">{{ summary.oracleOnly }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="dashboard-page__metric">
            <span class="dashboard-page__metric-label">ΔG 独有</span>
            <span class="dashboard-page__metric-value">{{ summary.gaussOnly }}</span>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="dashboard-page__metric">
            <span class="dashboard-page__metric-label">迁移覆盖率</span>
            <span class="dashboard-page__metric-value">
              {{ formatPercent(overallCoverage) }}
            </span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 差异矩阵 -->
    <diff-matrix
      :data="displayedDiffMatrix"
      :loading="isScanning || isInitializing || batchRefreshing"
      @select="handleSelect"
    >
      <template #actions>
        <diff-matrix-filters
          :filters="matrixFilters"
          :available-extensions="availableFileExtensions"
          :original-data="diffMatrix"
          @change="handleFilterChange"
          @reset="handleFilterReset"
        />
      </template>
    </diff-matrix>

    <!-- 多仓库导出对话框 -->
    <multi-repo-export-dialog
      :visible.sync="showMultiExportDialog"
    />

    <!-- 新增：添加配置对话框 -->
    <el-dialog 
      title="添加仓库配置" 
      :visible.sync="showAddConfig" 
      width="600px"
    >
      <el-form ref="form" :model="newConfig" :rules="configRules" label-width="120px" size="mini">
        <el-form-item label="配置名称" prop="name">
          <el-input v-model="newConfig.name" placeholder="请输入配置名称" />
        </el-form-item>
        
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="源仓库路径" prop="sourcePath">
              <el-input v-model="newConfig.sourcePath" placeholder="源仓库绝对路径" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标仓库路径" prop="targetPath">
              <el-input v-model="newConfig.targetPath" placeholder="目标仓库绝对路径" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="源代码标识" prop="sourceCode">
              <el-input v-model="newConfig.sourceCode" placeholder="如: oracle" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="目标代码标识" prop="targetCode">
              <el-input v-model="newConfig.targetCode" placeholder="如: gauss" />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 新增：时间范围配置 -->
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="扫描时间范围" prop="timeRange">
              <el-date-picker
                v-model="newConfig.timeRange"
                type="datetimerange"
                range-separator="至"
                start-placeholder="开始时间"
                end-placeholder="结束时间"
                format="yyyy-MM-dd HH:mm:ss"
                value-format="yyyy-MM-dd HH:mm:ss"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        
        <!-- 新增：扫描选项 -->
        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item>
              <el-checkbox v-model="newConfig.includeRemoteRefs">包含远程分支</el-checkbox>
              <el-checkbox v-model="newConfig.includeTags">包含标签</el-checkbox>
              <div style="margin-top: 8px">
                <span style="margin-right: 8px">最大引用数:</span>
                <el-input-number 
                  v-model="newConfig.maxRefs" 
                  :min="1" 
                  :max="1000" 
                  size="mini"
                  style="width: 80px"
                />
              </div>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      
      <div slot="footer">
        <el-button @click="showAddConfig = false">取消</el-button>
        <el-button type="primary" @click="saveConfig" :loading="saving">保存</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { mapState, mapGetters } from 'vuex';
import DiffMatrix from '../components/DiffMatrix.vue';
import DiffMatrixFilters from '../components/DiffMatrixFilters.vue';
import MultiRepoExportDialog from '../components/MultiRepoExportDialog.vue';

export default {
  name: 'DashboardPage',
  components: {
    DiffMatrix,
    DiffMatrixFilters,
    MultiRepoExportDialog,
  },
  data() {
    return {
      isInitializing: false, // 添加本地初始化状态
      showMultiExportDialog: false,
      showAddConfig: false,
      saving: false,
      newConfig: {
        name: '',
        sourcePath: '',
        targetPath: '',
        sourceCode: '',
        targetCode: '',
        timeRange: [],
        includeRemoteRefs: true,
        includeTags: false,
        maxRefs: 256
      },
      configRules: {
        name: [{ required: true, message: '请输入配置名称' }],
        sourcePath: [{ required: true, message: '请输入源仓库路径' }],
        targetPath: [{ required: true, message: '请输入目标仓库路径' }],
        sourceCode: [{ required: true, message: '请输入源代码标识' }],
        targetCode: [{ required: true, message: '请输入目标代码标识' }],
        timeRange: [{ type: 'array', required: true, message: '请选择扫描时间范围' }]
      }
    };
  },
  computed: {
    // 简化的状态映射
    ...mapState('diff', [
      'currentRepoId',
      'availableRepos',
      'isScanning',
      'lastScanTime',
      'taskId',
      'summary',
      'overallCoverage',
      'diffMatrix',
      'matrixFilters',
      'repoDataCache',
      'batchRefreshing',
      'batchRefreshProgress',
    ]),
    ...mapGetters('diff', {
      displayedDiffMatrix: 'filteredDiffMatrix',
      availableFileExtensions: 'availableFileExtensions',
    }),
    
    // 当前仓库信息
    currentRepoInfo() {
      if (!this.currentRepoId || !this.availableRepos.length) {
        return null;
      }
      return this.availableRepos.find(repo => repo.id === this.currentRepoId);
    },
    
    // 是否有数据
    hasData() {
      return this.diffMatrix && this.diffMatrix.length > 0;
    },
    
    // 批量刷新进度百分比
    batchProgressPercentage() {
      if (!this.batchRefreshProgress.total) return 0;
      return Math.round((this.batchRefreshProgress.current / this.batchRefreshProgress.total) * 100);
    },
  },
  async created() {
    await this.initializeDashboard();
  },
  methods: {
    // 初始化仪表板
    async initializeDashboard() {
      this.isInitializing = true;
      try {
        await this.$store.dispatch('diff/initializeRepoManagement');
      } catch (error) {
        console.error('初始化失败:', error);
        this.$message.error(`初始化失败: ${error.message}`);
      } finally {
        this.isInitializing = false;
      }
    },
    
    // 仓库切换处理
    async handleRepoChange(repoId) {
      if (!repoId) return;
      
      try {
        await this.$store.dispatch('diff/switchToRepo', repoId);
        this.$message.success(`已切换到 ${this.currentRepoInfo?.name || repoId}`);
      } catch (error) {
        console.error('切换仓库失败:', error);
        this.$message.error(`切换失败: ${error.message}`);
      }
    },
    
    // 刷新命令处理
    async handleRefreshCommand(command) {
      if (command === 'current') {
        await this.handleForceRefresh();
      } else if (command === 'all') {
        await this.handleBatchRefresh();
      }
    },
    
    // 强制刷新当前仓库
    async handleForceRefresh() {
      if (!this.currentRepoId) {
        this.$message.warning('请先选择仓库');
        return;
      }
      
      try {
        await this.$store.dispatch('diff/forceRefreshCurrentRepo');
        this.$message.success('刷新完成');
      } catch (error) {
        console.error('刷新失败:', error);
        this.$message.error(`刷新失败: ${error.message}`);
      }
    },
    
    // 批量刷新所有仓库
    async handleBatchRefresh() {
      if (!this.availableRepos.length) {
        this.$message.warning('没有可用的仓库配置');
        return;
      }
      
      try {
        const result = await this.$store.dispatch('diff/forceRefreshAllRepos');
        
        // 显示结果
        const successCount = result.success.length;
        const errorCount = result.errors.length;
        
        if (errorCount === 0) {
          this.$message.success(`成功刷新 ${successCount} 个仓库`);
        } else {
          this.$message.warning(`刷新完成：${successCount} 个成功，${errorCount} 个失败`);
        }
      } catch (error) {
        console.error('批量刷新失败:', error);
        this.$message.error(`批量刷新失败: ${error.message}`);
      }
    },
    
    // 矩阵行选择处理
    handleSelect(row = {}) {
      console.log('[DashboardPage] handleSelect called:', {
        row,
        taskId: this.taskId,
        currentRepoId: this.currentRepoId,
        hasData: this.hasData
      });
      
      if (!this.taskId) {
        console.warn('[DashboardPage] No taskId available');
        this.$message.warning('请先选择仓库或执行扫描');
        return;
      }
      
      const normalizedPath = typeof row.filePath === 'string' ? row.filePath.trim() : '';
      if (!normalizedPath) {
        console.error('[DashboardPage] File path is missing:', row);
        this.$message.error('文件路径缺失，请刷新数据后重试');
        return;
      }
      
      const routeQuery = this.buildDiffRouteQuery(row, normalizedPath);
      console.log('[DashboardPage] Navigating to FileDiff with query:', routeQuery);
      
      this.$message.info('正在加载文件详情...');
      
      this.$router.push({
        name: 'FileDiff',
        query: routeQuery,
      });
    },
    
    buildDiffRouteQuery(row, filePath) {
      const query = {
        taskId: this.taskId,
        filePath,
        oracleDelta: row?.oracleDelta || '',
        gaussDelta: row?.gaussDelta || '',
      };
      console.log('[DashboardPage] Built route query:', query);
      return query;
    },
    
    // 过滤器变化处理
    handleFilterChange(filters) {
      this.$store.commit('diff/setMatrixFilters', filters || {});
    },
    
    // 重置过滤器
    handleFilterReset() {
      this.$store.commit('diff/resetMatrixFilters');
    },
    
    // 打开多仓库导出对话框
    openMultiExportDialog() {
      this.showMultiExportDialog = true;
    },
    
    // 新增：显示添加配置对话框
    showAddConfigDialog() {
      this.showAddConfig = true;
    },
    
    // 新增：保存配置
    async saveConfig() {
      try {
        await this.$refs.form.validate();
        this.saving = true;
        
        // 解析时间范围
        const [timeFrom, timeTo] = this.newConfig.timeRange || [];
        
        // 调用后端API保存配置
        const response = await this.$http.post('/api/scan/add-preset', {
          name: this.newConfig.name,
          source: {
            path: this.newConfig.sourcePath,
            code: this.newConfig.sourceCode,
            scanStrategy: 'SNAPSHOT',
            timeFrom: timeFrom ? timeFrom.replace(' ', 'T') + '+08:00' : '2025-12-01T00:00:00+08:00',
            timeTo: timeTo ? timeTo.replace(' ', 'T') + '+08:00' : '2025-12-31T23:59:59+08:00',
            deltaType: 'DELTA_O',
            includeWorkingTree: false,
            fetchIfMissing: true,
            remoteName: 'origin',
            snapshotIncludeRemoteRefs: this.newConfig.includeRemoteRefs,
            snapshotIncludeTags: this.newConfig.includeTags,
            snapshotMaxRefs: this.newConfig.maxRefs
          },
          target: {
            path: this.newConfig.targetPath,
            code: this.newConfig.targetCode,
            scanStrategy: 'SNAPSHOT',
            timeFrom: timeFrom ? timeFrom.replace(' ', 'T') + '+08:00' : '2025-12-01T00:00:00+08:00',
            timeTo: timeTo ? timeTo.replace(' ', 'T') + '+08:00' : '2025-12-31T23:59:59+08:00',
            deltaType: 'DELTA_G',
            includeWorkingTree: false,
            fetchIfMissing: true,
            remoteName: 'origin',
            snapshotIncludeRemoteRefs: this.newConfig.includeRemoteRefs,
            snapshotIncludeTags: this.newConfig.includeTags,
            snapshotMaxRefs: this.newConfig.maxRefs
          }
        });
        
        if (response.data.status === 'success') {
          this.$message.success('配置已保存，请重启后端服务生效');
          this.showAddConfig = false;
          this.resetConfigForm();
          
          // 刷新配置列表
          await this.initializeDashboard();
        } else {
          this.$message.error('保存失败: ' + response.data.message);
        }
      } catch (error) {
        this.$message.error('保存失败: ' + error.message);
      } finally {
        this.saving = false;
      }
    },
    
    // 新增：重置配置表单
    resetConfigForm() {
      this.newConfig = {
        name: '',
        sourcePath: '',
        targetPath: '',
        sourceCode: '',
        targetCode: '',
        timeRange: [],
        includeRemoteRefs: true,
        includeTags: false,
        maxRefs: 256
      };
    },
    
    // 获取仓库缓存状态
    getRepoCacheStatus(repoId) {
      const cached = this.repoDataCache[repoId];
      if (!cached) return null;
      
      return {
        hasCache: true,
        cachedAt: cached.cachedAt,
        isCurrent: repoId === this.currentRepoId,
      };
    },
    
    // 格式化工具方法
    formatPercent(rate) {
      if (rate === undefined || rate === null || rate === '') {
        return '--';
      }
      return `${(Number(rate) * 100).toFixed(1)}%`;
    },
    
    formatDateTime(value) {
      if (!value) return '';
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return value;
      return date.toLocaleString();
    },
    
    formatCacheTime(value) {
      if (!value) return '';
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) return '';
      const now = new Date();
      const diff = now - date;
      
      // 小于1小时
      if (diff < 60 * 60 * 1000) {
        const minutes = Math.floor(diff / (60 * 1000));
        return `${minutes}分钟前`;
      }
      
      // 小于24小时
      if (diff < 24 * 60 * 60 * 1000) {
        const hours = Math.floor(diff / (60 * 60 * 1000));
        return `${hours}小时前`;
      }
      
      // 大于24小时，显示日期
      return date.toLocaleDateString();
    },
  },
};
</script>

<style scoped>
.dashboard-page__toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
  padding: 16px;
  background: #f8f9fa;
  border-radius: 8px;
}

.dashboard-page__toolbar-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.dashboard-page__repo-select {
  min-width: 280px;
}

.repo-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.repo-option__name {
  font-weight: 500;
}

.repo-option__cache {
  color: #67c23a;
  font-size: 12px;
}

.repo-option__no-cache {
  color: #909399;
  font-size: 12px;
}

.dashboard-page__repo-info {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
  color: #606266;
  flex-wrap: wrap;
}

.repo-info__name {
  font-weight: 600;
  color: #303133;
}

.repo-info__description {
  color: #909399;
}

.repo-info__scan-time {
  color: #909399;
  font-size: 12px;
}

.dashboard-page__hint {
  margin-bottom: 16px;
}

.dashboard-page__batch-progress {
  margin-bottom: 16px;
}

.batch-progress-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.batch-progress-bar {
  margin-bottom: 8px;
}

.batch-progress-text {
  font-size: 14px;
  color: #606266;
  text-align: center;
}

.batch-errors {
  margin-top: 16px;
  border-top: 1px solid #f56c6c;
  padding-top: 12px;
}

.error-title {
  font-weight: 600;
  color: #f56c6c;
  margin-bottom: 8px;
}

.error-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.error-item {
  padding: 8px;
  background: #fef0f0;
  border: 1px solid #f56c6c;
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.4;
}

.dashboard-page__summary {
  margin-bottom: 16px;
}

.dashboard-page__metric {
  display: flex;
  flex-direction: column;
  gap: 8px;
  text-align: center;
}

.dashboard-page__metric-label {
  color: #909399;
  font-size: 13px;
}

.dashboard-page__metric-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .dashboard-page__toolbar-group {
    flex-direction: column;
    align-items: stretch;
  }
  
  .dashboard-page__repo-select {
    width: 100%;
  }
  
  .dashboard-page__repo-info {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
}
</style>
