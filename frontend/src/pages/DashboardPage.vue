<template>
  <div class="dashboard-page">
    <div class="dashboard-page__toolbar">
      <el-select
        v-model="selectedPreset"
        placeholder="选择预设仓库"
        size="mini"
        :loading="loadingPresets"
        @change="handlePresetChange"
      >
        <el-option
          v-for="preset in presets"
          :key="preset.name"
          :label="preset.title || preset.name"
          :value="preset.name"
        />
      </el-select>
      <el-button type="primary" size="mini" @click="refreshDiffMatrix" :loading="loadingMatrix">
        刷新
      </el-button>
    </div>

    <el-row :gutter="16" class="dashboard-page__summary">
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

    <diff-matrix
      :data="displayedDiffMatrix"
      :loading="loadingMatrix"
      @select="handleSelect"
    >
      <template #actions>
        <diff-matrix-filters
          :filters="matrixFilters"
          @change="handleFilterChange"
          @reset="handleFilterReset"
        />
      </template>
    </diff-matrix>
  </div>
</template>

<script>
import { mapState, mapActions, mapMutations, mapGetters } from 'vuex';
import { fetchPresets } from '../api/diff';
import DiffMatrix from '../components/DiffMatrix.vue';
import DiffMatrixFilters from '../components/DiffMatrixFilters.vue';

export default {
  name: 'DashboardPage',
  components: {
    DiffMatrix,
    DiffMatrixFilters,
  },
  data() {
    return {
      presets: [],
      selectedPreset: '',
      loadingPresets: false,
    };
  },
  computed: {
    ...mapState('diff', [
      'taskId',
      'summary',
      'loadingMatrix',
      'overallCoverage',
      'matrixFilters',
    ]),
    ...mapGetters('diff', {
      displayedDiffMatrix: 'filteredDiffMatrix',
    }),
  },
  created() {
    this.initialize();
  },
  methods: {
    ...mapActions('diff', ['scanFull']),
    ...mapMutations('diff', ['setMatrixFilters', 'resetMatrixFilters']),
    async initialize() {
      await this.loadPresets();
    },
    async loadPresets() {
      this.loadingPresets = true;
      try {
        const list = await fetchPresets();
        this.presets = Array.isArray(list) ? list : [];
        if (!this.selectedPreset && this.presets.length > 0) {
          this.selectedPreset = this.presets[0].name;
        }
      } catch (error) {
        this.$message.error(error.message || '加载预设失败');
      } finally {
        this.loadingPresets = false;
      }
    },
    async refreshDiffMatrix() {
      if (!this.selectedPreset) {
        this.$message.warning('请选择预设仓库后再执行扫描');
        return;
      }
      try {
        const result = await this.scanFull({
          persistResult: true,
          presetName: this.selectedPreset,
        });
        if (result?.message) {
          this.$message.success(result.message);
        }
      } catch (error) {
        this.$message.error(error.message || '加载差异矩阵失败');
      }
    },
    handlePresetChange() {
      this.$message.info('预设已切换，请点击"刷新"拉取最新差异矩阵');
    },
    handleSelect(row) {
      this.$router.push({
        name: 'FileDiff',
        query: {
          taskId: this.taskId,
          filePath: row.filePath,
          oracleDelta: row.oracleDelta,
          gaussDelta: row.gaussDelta,
        },
      });
    },
    formatPercent(rate) {
      if (rate === undefined || rate === null || rate === '') {
        return '--';
      }
      return `${(Number(rate) * 100).toFixed(1)}%`;
    },
    handleFilterChange(filters) {
      this.setMatrixFilters(filters || {});
    },
    handleFilterReset() {
      this.resetMatrixFilters();
    },
  },
};
</script>

<style scoped>
.dashboard-page__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.dashboard-page__summary {
  margin-bottom: 16px;
}

.dashboard-page__metric {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
</style>
