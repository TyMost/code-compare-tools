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
  },
  created() {
    if (this.taskId && this.taskId !== this.storeTaskId) {
      this.setTaskId(this.taskId);
    }
    this.ensureCurrentFile(true);
  },
  methods: {
    ...mapActions('diff', ['fetchDetail', 'generateMigration', 'applyMigration', 'revertMigration']),
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
</style>
