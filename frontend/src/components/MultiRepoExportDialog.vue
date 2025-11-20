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
      <el-form label-width="90px" size="small" class="multi-export__form">
        <el-form-item label="状态筛选">
          <el-select
            v-model="form.statuses"
            placeholder="全部状态"
            filterable
            multiple
            collapse-tags
            style="width: 100%;"
          >
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="覆盖率">
          <div class="multi-export__slider">
            <el-slider
              v-model="form.coverageRange"
              range
              :min="0"
              :max="100"
              :step="1"
              :show-tooltip="false"
            />
            <div class="multi-export__range">
              {{ form.coverageRange[0] }}% - {{ form.coverageRange[1] }}%
            </div>
          </div>
          <el-checkbox v-model="form.includeEmptyCoverage">
            包含覆盖率为空的文件
          </el-checkbox>
        </el-form-item>
      </el-form>
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
import { mapState, mapActions } from 'vuex';

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
      form: {
        statuses: [],
        coverageRange: [0, 100],
        includeEmptyCoverage: true,
      },
      statusOptions: STATUS_OPTIONS,
    };
  },
  computed: {
    ...mapState('diff', [
      'availableTasks',
      'loadingTasks',
      'matrixFilters',
      'exportingReport',
    ]),
    tasks() {
      return Array.isArray(this.availableTasks) ? this.availableTasks : [];
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
      this.form.statuses = Array.isArray(filters.statuses) ? [...filters.statuses] : [];
      const range = Array.isArray(filters.coverageRange)
        ? filters.coverageRange
        : [0, 1];
      this.form.coverageRange = range.map((value, index) => {
        const numeric = Number(value);
        if (Number.isNaN(numeric)) {
          return index === 0 ? 0 : 100;
        }
        return Math.round(Math.min(Math.max(numeric, 0), 1) * 100);
      });
      this.form.includeEmptyCoverage =
        filters.includeEmptyCoverage === undefined
          ? true
          : !!filters.includeEmptyCoverage;
    },
    resetFilters() {
      this.syncFiltersFromStore();
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
        statuses: this.form.statuses,
        coverageRange: this.form.coverageRange.map((value, index) => {
          const numeric = Number(value);
          if (Number.isNaN(numeric)) {
            return index === 0 ? 0 : 1;
          }
          const normalized = Math.min(Math.max(numeric, 0), 100);
          return Number((normalized / 100).toFixed(4));
        }),
        includeEmptyCoverage: this.form.includeEmptyCoverage,
      };
      try {
        await this.exportMultiReport({ repos, filters, format: 'csv' });
        this.$message.success('报表生成成功，已开始下载');
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
