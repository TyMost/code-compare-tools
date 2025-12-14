<template>
  <el-dialog
    :visible.sync="internalVisible"
    title="导出多仓报表"
    width="760px"
    @close="handleClose"
  >
    <div class="multi-export__section">
      <div class="multi-export__header">
        <span>选择需要合并的扫描任务</span>
        <el-button type="text" size="mini" @click="refreshTasks" :loading="loadingTasks">
          刷新列表
        </el-button>
      </div>
      <el-table
        v-loading="loadingTasks"
        :data="tasks"
        height="240"
        size="mini"
        border
        row-key="taskId"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="50" />
        <el-table-column prop="presetName" label="预设/仓库" min-width="140">
          <template slot-scope="{ row }">
            {{ row.presetName || row.taskId }}
          </template>
        </el-table-column>
        <el-table-column prop="mode" label="模式" width="80" />
        <el-table-column prop="generatedAt" label="扫描时间" min-width="150">
          <template slot-scope="{ row }">
            {{ formatTime(row.generatedAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="totalFiles" label="文件数" width="80" />
        <el-table-column prop="overallCoverage" label="覆盖率" width="100">
          <template slot-scope="{ row }">
            {{ formatPercent(row.overallCoverage) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="multi-export__section">
      <div class="multi-export__header">
        <span>导出格式选择</span>
        <el-tooltip content="CSV格式兼容性好，Excel格式显示效果更佳" placement="top">
          <i class="el-icon-question" style="color: #909399;"></i>
        </el-tooltip>
      </div>
      <el-radio-group v-model="exportFormat" size="small">
        <el-radio label="csv">
          <i class="el-icon-document"></i> CSV格式
        </el-radio>
        <el-radio label="excel">
          <i class="el-icon-s-grid"></i> Excel格式
        </el-radio>
      </el-radio-group>
      <div class="format-description">
        <span v-if="exportFormat === 'csv'" class="format-tip">
          CSV格式：兼容性佳，支持各种数据分析工具
        </span>
        <span v-else class="format-tip">
          Excel格式：多Sheet展示，布局更清晰，支持数据筛选
        </span>
      </div>
    </div>

    <div class="multi-export__section">
      <div class="multi-export__header">
        <span>提交信息设置</span>
        <el-tooltip content="包含提交信息会显著增加导出时间，建议仅在需要时开启" placement="top">
          <i class="el-icon-warning" style="color: #E6A23C;"></i>
        </el-tooltip>
      </div>
      <div class="commit-info-setting">
        <el-switch
          v-model="exportFilters.includeCommitInfo"
          active-text="包含提交信息"
          inactive-text="仅文件对比"
          active-color="#67C23A"
          inactive-color="#909399"
          @change="handleCommitInfoToggle"
        />
        <div v-if="exportFilters.includeCommitInfo" class="commit-info-warning">
          <i class="el-icon-warning-outline"></i>
          <span>开启提交信息将显著增加导出时间（可能增加3-5倍），请确认是否需要</span>
        </div>
      </div>
    </div>

    <div class="multi-export__section">
      <div class="multi-export__header">
        <span>导出筛选条件</span>
        <el-button type="text" size="mini" @click="resetFilters">
          同步当前页面筛选
        </el-button>
      </div>
      <diff-matrix-filters
        :filters="exportFilters"
        :available-extensions="availableFileExtensions"
        :original-data="allDiffMatrixData"
        :available-authors="availableAuthors"
        @change="handleFilterChange"
      />
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose" :disabled="exportingReport">取 消</el-button>
      <el-button
        type="primary"
        :disabled="selectedTaskIds.length === 0 || exportingReport"
        :loading="exportingReport"
        @click="handleExport"
      >
        <span v-if="!exportingReport">导出 {{ exportFormat.toUpperCase() }}</span>
        <span v-else>{{ exportProgressText }}</span>
      </el-button>
      <el-button
        v-if="currentAsyncTask && currentAsyncTask.isDownloadable"
        type="success"
        @click="downloadCurrentAsyncTask"
      >
        下载文件
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import { mapState, mapActions, mapGetters } from 'vuex';
import DiffMatrixFilters from './DiffMatrixFilters.vue';

const STATUS_OPTIONS = [
  { value: 'matched', label: '已匹配' },
  { value: 'partial', label: '存在差异' },
  { value: 'oracle-only', label: '仅 ΔO' },
  { value: 'gauss-only', label: '仅 ΔG' },
  { value: 'pending', label: '待处理' },
  { value: 'processing', label: '处理中' },
];

export default {
  name: 'MultiRepoExportDialog',
  components: {
    DiffMatrixFilters,
  },
  props: {
    visible: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      internalVisible: this.visible,
      selectedTaskIds: [],
      exportFormat: 'csv',
      exportFilters: {
        statuses: [],
        coverageRange: [0, 1],
        includeEmptyCoverage: true,
        fileExtensions: [],
        excludeTestFiles: false,
        excludePatterns: [],
        includeCommitInfo: false,
        authorFilters: [],
        authorTypeFilter: '',
        timeRange: [],
      },
      exportProgressText: '',
    };
  },
  computed: {
    ...mapState('diff', [
      'availableTasks',
      'loadingTasks',
      'matrixFilters',
      'exportingReport',
      'exportProgress',
      'diffMatrix',
      'currentAsyncTask',
    ]),
    ...mapGetters('diff', {
      availableFileExtensions: 'availableFileExtensions',
      filteredDiffMatrix: 'filteredDiffMatrix',
    }),
    tasks() {
      return Array.isArray(this.availableTasks) ? this.availableTasks : [];
    },
    allDiffMatrixData() {
      return Array.isArray(this.diffMatrix) ? this.diffMatrix : [];
    },
    // 计算当前筛选条件的活跃数量，用于用户提示
    activeFiltersCount() {
      let count = 0;
      const filters = this.exportFilters || {};
      
      if (filters.statuses && filters.statuses.length > 0) count++;
      if (filters.fileExtensions && filters.fileExtensions.length > 0) count++;
      if (!filters.includeEmptyCoverage) count++;
      if (filters.excludeTestFiles) count++;
      if (filters.excludePatterns && filters.excludePatterns.length > 0) count++;
      if (filters.includeCommitInfo) count++;
      if (filters.authorFilters && filters.authorFilters.length > 0) count++;
      if (filters.authorTypeFilter) count++;
      if (filters.timeRange && filters.timeRange.length > 0) count++;
      
      return count;
    },
    // 计算可用的提交者列表，用于筛选
    availableAuthors() {
      if (!Array.isArray(this.allDiffMatrixData)) return [];
      
      const authorMap = new Map();
      
      this.allDiffMatrixData.forEach(row => {
        // Oracle提交者
        if (row.lastOracleAuthor) {
          const key = row.lastOracleAuthor;
          const existing = authorMap.get(key);
          if (existing) {
            existing.count += (row.oracleCommitCount || 0);
          } else {
            authorMap.set(key, {
              value: row.lastOracleAuthor,
              label: row.lastOracleAuthor,
              count: row.oracleCommitCount || 0
            });
          }
        }
        
        // Gauss提交者
        if (row.lastGaussAuthor) {
          const key = row.lastGaussAuthor;
          const existing = authorMap.get(key);
          if (existing) {
            existing.count += (row.gaussCommitCount || 0);
          } else {
            authorMap.set(key, {
              value: row.lastGaussAuthor,
              label: row.lastGaussAuthor,
              count: row.gaussCommitCount || 0
            });
          }
        }
      });
      
      return Array.from(authorMap.values()).sort((a, b) => b.count - a.count);
    },
  },
  watch: {
    visible: {
      immediate: true,
      handler(value) {
        this.internalVisible = value;
        if (value) {
          this.syncFiltersFromStore();
          this.ensureTasks();
        } else {
          this.selectedTaskIds = [];
        }
      },
    },
    internalVisible(value) {
      if (!value) {
        this.$emit('update:visible', false);
      }
    },
    // 监听store中筛选条件的变化，实时同步到导出对话框
    matrixFilters: {
      deep: true,
      handler() {
        if (this.internalVisible) {
          this.syncFiltersFromStore();
        }
      },
    },
    // 监听导出进度，更新进度文本
    exportProgress: {
      deep: true,
      handler(progress) {
        if (progress.stage) {
          this.updateExportProgressText(progress);
        }
      },
    },
  },
  methods: {
    ...mapActions('diff', ['fetchRecentTasks', 'exportMultiReport', 'smartExport', 'downloadAsyncExportFile']),
    handleClose() {
      this.internalVisible = false;
      this.$emit('update:visible', false);
    },
    async ensureTasks() {
      if (!this.tasks.length) {
        await this.fetchRecentTasks();
      }
    },
    refreshTasks() {
      return this.fetchRecentTasks();
    },
    syncFiltersFromStore() {
      const filters = this.matrixFilters || {};
      this.exportFilters = {
        statuses: Array.isArray(filters.statuses) ? [...filters.statuses] : [],
        coverageRange: Array.isArray(filters.coverageRange) ? [...filters.coverageRange] : [0, 1],
        includeEmptyCoverage: filters.includeEmptyCoverage === undefined ? true : !!filters.includeEmptyCoverage,
        fileExtensions: Array.isArray(filters.fileExtensions) ? [...filters.fileExtensions] : [],
        excludeTestFiles: filters.excludeTestFiles || false,
        excludePatterns: Array.isArray(filters.excludePatterns) ? [...filters.excludePatterns] : [],
        includeCommitInfo: filters.includeCommitInfo === undefined ? false : !!filters.includeCommitInfo,
        authorFilters: Array.isArray(filters.authorFilters) ? [...filters.authorFilters] : [],
        authorTypeFilter: filters.authorTypeFilter || '',
        timeRange: Array.isArray(filters.timeRange) ? [...filters.timeRange] : [],
      };
    },
    resetFilters() {
      this.syncFiltersFromStore();
      this.$message.info('已同步当前页面的筛选条件');
    },
    handleFilterChange(filters) {
      this.exportFilters = { ...filters };
    },
    handleSelectionChange(rows) {
      this.selectedTaskIds = (rows || []).map((row) => row.taskId);
    },
    selectedReposPayload() {
      return this.tasks
        .filter((item) => this.selectedTaskIds.includes(item.taskId))
        .map((item) => ({
          taskId: item.taskId,
          alias: item.presetName || item.taskId,
        }));
    },
    handleCommitInfoToggle(value) {
      if (value) {
        this.$message.warning('开启提交信息将显著增加导出时间，建议仅在需要时使用');
      }
    },
    async handleExport() {
      if (this.selectedTaskIds.length === 0) {
        this.$message.warning('请至少选择一个任务');
        return;
      }
      const repos = this.selectedReposPayload();
      if (repos.length === 0) {
        this.$message.warning('选择的任务列表为空');
        return;
      }
      
      // 构建完整的筛选条件，包含提交信息相关字段
      const filters = {
        statuses: this.exportFilters.statuses,
        coverageRange: this.exportFilters.coverageRange,
        includeEmptyCoverage: this.exportFilters.includeEmptyCoverage,
        fileExtensions: this.exportFilters.fileExtensions,
        excludeTestFiles: this.exportFilters.excludeTestFiles,
        excludePatterns: this.exportFilters.excludePatterns,
        includeCommitInfo: this.exportFilters.includeCommitInfo,
        authorFilters: this.exportFilters.authorFilters,
        authorTypeFilter: this.exportFilters.authorTypeFilter,
        timeFrom: this.exportFilters.timeRange && this.exportFilters.timeRange.length > 0 
          ? new Date(this.exportFilters.timeRange[0]).toISOString() 
          : null,
        timeTo: this.exportFilters.timeRange && this.exportFilters.timeRange.length > 1 
          ? new Date(this.exportFilters.timeRange[1]).toISOString() 
          : null,
      };
      
      // 提供用户友好的导出信息
      const filterInfo = [];
      if (filters.statuses && filters.statuses.length > 0) {
        filterInfo.push(`状态: ${filters.statuses.join(', ')}`);
      }
      if (filters.fileExtensions && filters.fileExtensions.length > 0) {
        filterInfo.push(`文件类型: ${filters.fileExtensions.join(', ')}`);
      }
      if (!filters.includeEmptyCoverage) {
        filterInfo.push('排除空覆盖率');
      }
      if (filters.excludeTestFiles) {
        filterInfo.push('排除测试文件');
      }
      if (filters.excludePatterns && filters.excludePatterns.length > 0) {
        filterInfo.push(`排除规则: ${filters.excludePatterns.length}个`);
      }
      if (filters.includeCommitInfo) {
        filterInfo.push('包含提交信息');
        if (filters.authorFilters && filters.authorFilters.length > 0) {
          filterInfo.push(`提交者: ${filters.authorFilters.join(', ')}`);
        }
        if (filters.authorTypeFilter) {
          filterInfo.push(`提交者类型: ${filters.authorTypeFilter}`);
        }
        if (filters.timeFrom && filters.timeTo) {
          const from = new Date(filters.timeFrom).toLocaleString();
          const to = new Date(filters.timeTo).toLocaleString();
          filterInfo.push(`时间范围: ${from} 至 ${to}`);
        }
      }
      
      const filterSummary = filterInfo.length > 0 ? ` (应用筛选: ${filterInfo.join(', ')})` : ' (导出全部数据)';
      
      try {
        // 使用智能导出：自动选择同步或异步导出
        const result = await this.smartExport({ repos, filters, format: this.exportFormat });
        
        if (result && result.taskId) {
          // 异步导出
          this.$message.info(`异步导出任务已创建，任务ID: ${result.taskId}${filterSummary}`);
        } else {
          // 同步导出成功
          this.$message.success(`报表生成成功，已开始下载${filterSummary}`);
          this.handleClose();
        }
      } catch (error) {
        this.$message.error(error.message || '导出失败');
      }
    },

    // 下载当前异步任务的文件
    async downloadCurrentAsyncTask() {
      if (!this.currentAsyncTask || !this.currentAsyncTask.taskId) {
        this.$message.warning('没有可下载的任务');
        return;
      }
      
      try {
        await this.downloadAsyncExportFile(this.currentAsyncTask.taskId);
        this.$message.success('文件下载成功');
      } catch (error) {
        this.$message.error('下载失败: ' + (error.message || error));
      }
    },
    formatPercent(value) {
      if (value === null || value === undefined) {
        return '--';
      }
      const numeric = Number(value);
      if (Number.isNaN(numeric)) {
        return '--';
      }
      return `${(numeric * 100).toFixed(1)}%`;
    },
    formatTime(value) {
      if (!value) {
        return '--';
      }
      const date = new Date(value);
      if (Number.isNaN(date.getTime())) {
        return value;
      }
      return date.toLocaleString();
    },
    updateExportProgressText(progress) {
      const { stage, message, current, total, percentage } = progress;
      
      if (percentage && percentage > 0) {
        this.exportProgressText = `${stage} ${percentage}%`;
      } else if (current && total && total > 0) {
        const progressPercent = Math.round((current / total) * 100);
        this.exportProgressText = `${stage} ${progressPercent}%`;
      } else if (message) {
        this.exportProgressText = message;
      } else {
        this.exportProgressText = stage || '正在导出...';
      }
    },
  },
};
</script>

<style scoped>
.multi-export__section + .multi-export__section {
  margin-top: 16px;
}

.multi-export__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-weight: 500;
}

.multi-export__slider {
  padding: 0 4px;
}

.multi-export__range {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin-top: 4px;
}

.multi-export__form {
  margin-top: 8px;
}

.format-description {
  margin-top: 8px;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
}

.format-tip {
  color: #606266;
  line-height: 1.4;
}

.commit-info-setting {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.commit-info-warning {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background-color: #FDF6EC;
  border: 1px solid #FAECD8;
  border-radius: 4px;
  font-size: 12px;
  color: #E6A23C;
}

.commit-info-warning i {
  font-size: 14px;
}
</style>
