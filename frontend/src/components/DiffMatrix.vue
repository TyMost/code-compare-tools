<template>
  <el-card class="diff-matrix-card">
    <div slot="header" class="diff-matrix-card__header">
      <span>ΔO vs ΔG 差异矩阵</span>
      <div class="diff-matrix-card__actions">
        <slot name="actions" />
      </div>
    </div>
    <el-table
      :data="data"
      :loading="loading"
      stripe
      height="480"
      empty-text="暂无差异数据"
      @row-click="handleRowClick"
    >
      <el-table-column type="index" label="#" width="60" />
      <el-table-column prop="filePath" label="文件路径" min-width="240">
        <template slot-scope="{ row }">
          <span class="diff-matrix-card__path">{{ row.filePath }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="oracleDelta" label="ΔO(o1→o2)" width="160" />
      <el-table-column prop="gaussDelta" label="ΔG(g1→g2)" width="160" />
      <el-table-column label="覆盖率" width="120">
        <template slot-scope="{ row }">
          {{ formatCoverage(row.coverage) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template slot-scope="{ row }">
          <el-button type="text" size="small" @click.stop="emitOpen(row)">
            查看
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script>
export default {
  name: 'DiffMatrix',
  props: {
    data: {
      type: Array,
      default: () => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  methods: {
    handleRowClick(row) {
      this.emitOpen(row);
    },
    emitOpen(row) {
      this.$emit('select', row);
    },
    formatCoverage(value) {
      if (value === undefined || value === null || value === '') {
        return '--';
      }
      const numeric = Number(value);
      if (Number.isNaN(numeric)) {
        return '--';
      }
      return `${(numeric * 100).toFixed(1)}%`;
    },
    statusLabel(status) {
      switch (status) {
        case 'matched':
          return '已匹配';
        case 'oracle-only':
          return '仅 ΔO';
        case 'gauss-only':
          return '仅 ΔG';
        case 'pending':
          return '待处理';
        case 'processing':
          return '处理中';
        case 'partial':
          return '存在差异';
        default:
          return '待分析';
      }
    },
    statusTagType(status) {
      switch (status) {
        case 'matched':
          return 'success';
        case 'oracle-only':
        case 'gauss-only':
          return 'warning';
        case 'partial':
          return 'danger';
        case 'processing':
          return 'info';
        default:
          return 'info';
      }
    },
  },
};
</script>

<style scoped>
.diff-matrix-card {
  width: 100%;
}

.diff-matrix-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.diff-matrix-card__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.diff-matrix-card__path {
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 13px;
}
</style>
