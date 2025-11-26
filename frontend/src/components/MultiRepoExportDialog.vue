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
        <span>导出筛选条件</span>
        <el-button type="text" size="mini" @click="resetFilters">
          同步当前页面筛选
        </el-button>
      </div>
      <diff-matrix-filters
        :filters="exportFilters"
        :available-extensions="availableFileExtensions"
        :original-data="allDiffMatrixData"
        @change="handleFilterChange"
      />
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="handleClose">取 消</el-button>
      <el-button
        type="primary"
        :disabled="selectedTaskIds.length === 0"
        :loading="exportingReport"
        @click="handleExport"
      >
        导出 CSV
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
      exportFilters: {
        statuses: [],
        coverageRange: [0, 1],
        includeEmptyCoverage: true,
        fileExtensions: [],
        excludeTestFiles: false,
        excludePatterns: [],
      },
    };
  },
  computed: {
    ...mapState('diff', [
      'availableTasks',
      'loadingTasks',
      'matrixFilters',
      'exportingReport',
      'diffMatrix',
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
      
      return count;
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
  },
  methods: {
    ...mapActions('diff', ['fetchRecentTasks', 'exportMultiReport']),
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
      const filters = {
        statuses: this.exportFilters.statuses,
        coverageRange: this.exportFilters.coverageRange,
        includeEmptyCoverage: this.exportFilters.includeEmptyCoverage,
        fileExtensions: this.exportFilters.fileExtensions,
        excludeTestFiles: this.exportFilters.excludeTestFiles,
        excludePatterns: this.exportFilters.excludePatterns,
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
      
      const filterSummary = filterInfo.length > 0 ? ` (应用筛选: ${filterInfo.join(', ')})` : ' (导出全部数据)';
      
      try {
        await this.exportMultiReport({ repos, filters, format: 'csv' });
        this.$message.success(`报表生成成功，已开始下载${filterSummary}`);
        this.handleClose();
      } catch (error) {
        this.$message.error(error.message || '导出失败');
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
</style>
