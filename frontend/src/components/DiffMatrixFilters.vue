<template>
  <el-popover
    v-model="visible"
    placement="bottom-end"
    trigger="click"
    width="360"
  >
    <div class="diff-matrix-filters__section">
      <div class="diff-matrix-filters__label">状态</div>
      <el-select
        v-model="localFilters.statuses"
        placeholder="全部状态"
        size="mini"
        multiple
        collapse-tags
        filterable
      >
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        />
      </el-select>
    </div>

    <div class="diff-matrix-filters__section">
      <div class="diff-matrix-filters__label">文件类型</div>
      <el-select
        v-model="localFilters.fileExtensions"
        placeholder="全部文件类型"
        size="mini"
        multiple
        collapse-tags
        filterable
        :max-collapse-tags="3"
      >
        <el-option
          v-for="ext in availableExtensions"
          :key="ext"
          :label="ext"
          :value="ext"
        >
          <span style="float: left">{{ ext }}</span>
          <span style="float: right; color: #8492a6; font-size: 12px">
            {{ getFileCount(ext) }} 个文件
          </span>
        </el-option>
      </el-select>
    </div>

    <div class="diff-matrix-filters__section">
      <div class="diff-matrix-filters__label">覆盖率</div>
      <div class="diff-matrix-filters__slider">
        <el-slider
          v-model="localFilters.coverageRange"
          range
          :min="0"
          :max="100"
          :step="1"
          :show-tooltip="false"
        />
        <div class="diff-matrix-filters__range">
          {{ localFilters.coverageRange[0] }}% - {{ localFilters.coverageRange[1] }}%
        </div>
      </div>
      <el-checkbox v-model="localFilters.includeEmptyCoverage">
        包含覆盖率为空的文件
      </el-checkbox>
    </div>

    <div class="diff-matrix-filters__actions">
      <el-button size="mini" @click="handleReset">重置</el-button>
      <el-button type="primary" size="mini" @click="applyFilters">应用</el-button>
    </div>

    <el-button
      slot="reference"
      type="text"
      size="mini"
      class="diff-matrix-filters__trigger"
      :class="{ 'diff-matrix-filters__trigger--active': hasActiveFilters }"
    >
      <i class="el-icon-filter" />
      筛选
    </el-button>
  </el-popover>
</template>

<script>
const STATUS_OPTIONS = [
  { value: 'matched', label: '已匹配' },
  { value: 'partial', label: '存在差异' },
  { value: 'oracle-only', label: '仅 ΔO' },
  { value: 'gauss-only', label: '仅 ΔG' },
  { value: 'pending', label: '待处理' },
  { value: 'processing', label: '处理中' },
];

export default {
  name: 'DiffMatrixFilters',
  props: {
    filters: {
      type: Object,
      default: () => ({}),
    },
    availableExtensions: {
      type: Array,
      default: () => [],
    },
    originalData: {
      type: Array,
      default: () => [],
    },
  },
  data() {
    return {
      visible: false,
      localFilters: this.normalizeFilters(this.filters),
      statusOptions: STATUS_OPTIONS,
    };
  },
  computed: {
    hasActiveFilters() {
      const incoming = this.filters || {};
      const statuses = Array.isArray(incoming.statuses) ? incoming.statuses : [];
      const [min = 0, max = 1] = Array.isArray(incoming.coverageRange)
        ? incoming.coverageRange
        : [0, 1];
      const includeEmpty =
        incoming.includeEmptyCoverage === undefined
          ? true
          : !!incoming.includeEmptyCoverage;
      const fileExtensions = Array.isArray(incoming.fileExtensions) ? incoming.fileExtensions : [];
      return (
        statuses.length > 0 ||
        Number(min) > 0 ||
        Number(max) < 1 ||
        includeEmpty === false ||
        fileExtensions.length > 0
      );
    },
  },
  watch: {
    filters: {
      deep: true,
      handler() {
        this.localFilters = this.normalizeFilters(this.filters);
      },
    },
  },
  methods: {
    normalizeFilters(source = {}) {
      const statuses = Array.isArray(source.statuses) ? [...source.statuses] : [];
      const includeEmpty =
        source.includeEmptyCoverage === undefined
          ? true
          : !!source.includeEmptyCoverage;
      const rawRange = Array.isArray(source.coverageRange)
        ? source.coverageRange
        : [0, 1];
      const coverageRange =
        rawRange.length === 2
          ? rawRange.map((value, index) => {
              const numeric = Number(value);
              const fallback = index === 0 ? 0 : 1;
              const normalized = Number.isNaN(numeric) ? fallback : numeric;
              const bounded = Math.min(Math.max(normalized, 0), 1);
              return Math.round(bounded * 100);
            })
          : [0, 100];
      const fileExtensions = Array.isArray(source.fileExtensions) ? [...source.fileExtensions] : [];
      return {
        statuses,
        coverageRange,
        includeEmptyCoverage: includeEmpty,
        fileExtensions,
      };
    },
    applyFilters() {
      const normalizedRange = Array.isArray(this.localFilters.coverageRange)
        ? this.localFilters.coverageRange.map((value) => {
            const numeric = Number(value);
            const safe = Number.isNaN(numeric) ? 0 : numeric;
            const clamped = Math.min(Math.max(safe, 0), 100);
            return Number((clamped / 100).toFixed(4));
          })
        : [0, 1];
      this.$emit('change', {
        statuses: Array.isArray(this.localFilters.statuses)
          ? [...this.localFilters.statuses]
          : [],
        coverageRange: normalizedRange,
        includeEmptyCoverage: this.localFilters.includeEmptyCoverage,
        fileExtensions: Array.isArray(this.localFilters.fileExtensions)
          ? [...this.localFilters.fileExtensions]
          : [],
      });
      this.visible = false;
    },
    getFileCount(extension) {
      if (!Array.isArray(this.originalData) || !extension) return 0;
      return this.originalData.filter(item => {
        const fileExt = item.filePath.split('.').pop();
        return fileExt ? `.${fileExt.toLowerCase()}` === extension.toLowerCase() : false;
      }).length;
    },
    handleReset() {
      this.$emit('reset');
      this.visible = false;
    },
  },
};
</script>

<style scoped>
.diff-matrix-filters__trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.diff-matrix-filters__trigger--active {
  color: #409eff;
}

.diff-matrix-filters__section + .diff-matrix-filters__section {
  margin-top: 12px;
}

.diff-matrix-filters__label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}

.diff-matrix-filters__slider {
  padding: 0 4px;
}

.diff-matrix-filters__range {
  font-size: 12px;
  color: #909399;
  text-align: center;
  margin-top: 4px;
}

.diff-matrix-filters__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
</style>
