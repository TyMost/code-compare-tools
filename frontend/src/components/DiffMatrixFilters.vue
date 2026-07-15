<template>
  <el-popover
    v-model="visible"
    placement="bottom-end"
    trigger="click"
    width="400"
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

    <div class="diff-matrix-filters__section">
      <div class="diff-matrix-filters__label">提交信息</div>
      <el-checkbox v-model="localFilters.includeCommitInfo">
        包含提交信息
      </el-checkbox>
    </div>

    <div class="diff-matrix-filters__section">
      <div class="diff-matrix-filters__label">排除规则</div>
      <el-checkbox v-model="localFilters.excludeTestFiles" @change="handleExcludeTestFilesChange">
        排除测试文件
      </el-checkbox>
      <div class="diff-matrix-filters__exclude-patterns" v-if="showExcludePatterns">
        <div class="diff-matrix-filters__pattern-header">
          <span>自定义排除规则：</span>
          <el-button type="text" size="mini" @click="addExcludePattern">
            <i class="el-icon-plus"></i> 添加规则
          </el-button>
        </div>
        <div class="diff-matrix-filters__pattern-list">
          <div 
            v-for="(pattern, index) in localFilters.excludePatterns" 
            :key="index"
            class="diff-matrix-filters__pattern-item"
          >
            <el-input
              v-model="localFilters.excludePatterns[index]"
              placeholder="如: *Test*.java 或 /.*\.test\.js/"
              size="mini"
              class="diff-matrix-filters__pattern-input"
              aria-label="排除规则"
            />
            <el-button 
              type="text" 
              size="mini" 
              @click="removeExcludePattern(index)"
              class="diff-matrix-filters__pattern-remove"
              aria-label="删除规则"
              title="删除规则"
            >
              <i class="el-icon-delete"></i>
            </el-button>
          </div>
        </div>
        <div class="diff-matrix-filters__pattern-tips">
          <div class="diff-matrix-filters__tip">
            支持通配符: * (任意字符) 和 ? (单个字符)
          </div>
          <div class="diff-matrix-filters__tip">
            支持正则表达式: 用 / 包裹，如 /.*\.test\.js/
          </div>
        </div>
      </div>
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
    availableAuthors: {
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
      const excludePatterns = Array.isArray(incoming.excludePatterns) ? incoming.excludePatterns : [];
      return (
        statuses.length > 0 ||
        Number(min) > 0 ||
        Number(max) < 1 ||
        includeEmpty === false ||
        fileExtensions.length > 0 ||
        excludePatterns.length > 0 ||
        incoming.excludeTestFiles === true ||
        incoming.includeCommitInfo === true
      );
    },
    showExcludePatterns() {
      return this.localFilters.excludeTestFiles || 
             (Array.isArray(this.localFilters.excludePatterns) && this.localFilters.excludePatterns.length > 0);
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
      const excludePatterns = Array.isArray(source.excludePatterns) ? [...source.excludePatterns] : [];
      const excludeTestFiles = source.excludeTestFiles === undefined ? false : !!source.excludeTestFiles;
      const includeCommitInfo = source.includeCommitInfo === undefined ? false : !!source.includeCommitInfo;
      
      return {
        statuses,
        coverageRange,
        includeEmptyCoverage: includeEmpty,
        fileExtensions,
        excludePatterns,
        excludeTestFiles,
        includeCommitInfo,
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
        excludePatterns: Array.isArray(this.localFilters.excludePatterns)
          ? [...this.localFilters.excludePatterns].filter(pattern => pattern.trim() !== '')
          : [],
        excludeTestFiles: this.localFilters.excludeTestFiles || false,
        includeCommitInfo: this.localFilters.includeCommitInfo || false,
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
    handleExcludeTestFilesChange(value) {
      if (value) {
        // 添加默认的测试文件排除模式
        const defaultTestPatterns = [
          '*Test.java',
          '*test.java', 
        ];
        
        // 合并现有模式，避免重复
        const existingPatterns = this.localFilters.excludePatterns || [];
        const newPatterns = defaultTestPatterns.filter(pattern => 
          !existingPatterns.includes(pattern)
        );
        
        this.localFilters.excludePatterns = [...existingPatterns, ...newPatterns];
      }
    },
    addExcludePattern() {
      if (!this.localFilters.excludePatterns) {
        this.localFilters.excludePatterns = [];
      }
      this.localFilters.excludePatterns.push('');
    },
    removeExcludePattern(index) {
      if (this.localFilters.excludePatterns && index >= 0) {
        this.localFilters.excludePatterns.splice(index, 1);
      }
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

/* 排除规则样式 */
.diff-matrix-filters__exclude-patterns {
  margin-top: 8px;
  padding-left: 20px;
  border-left: 2px solid #e4e7ed;
}

.diff-matrix-filters__pattern-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 12px;
  color: #606266;
}

.diff-matrix-filters__pattern-list {
  margin-bottom: 8px;
}

.diff-matrix-filters__pattern-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.diff-matrix-filters__pattern-input {
  flex: 1;
}

.diff-matrix-filters__pattern-remove {
  color: #f56c6c;
  padding: 4px;
}

.diff-matrix-filters__pattern-remove:hover {
  background-color: #fef0f0;
}

.diff-matrix-filters__pattern-tips {
  font-size: 11px;
  color: #909399;
  line-height: 1.4;
}

.diff-matrix-filters__tip {
  margin-bottom: 2px;
}
</style>
