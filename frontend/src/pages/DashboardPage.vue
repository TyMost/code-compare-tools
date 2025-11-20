<template>
  <div class="dashboard-page">
    <div class="dashboard-page__toolbar">
      <div class="dashboard-page__toolbar-group">
        <el-select
          v-model="activeSnapshotKey"
          placeholder="选择缓存结果"
          size="mini"
          class="dashboard-page__snapshot-select"
          :loading="loadingSnapshots"
          @change="handleSnapshotChange"
        >
          <el-option
            v-for="snapshot in cachedSnapshots"
            :key="snapshotKey(snapshot)"
            :label="formatSnapshotLabel(snapshot)"
            :value="snapshotKey(snapshot)"
          />
        </el-select>
        <el-button size="mini" @click="reloadSnapshots" :loading="loadingSnapshots">
          重载缓存
        </el-button>
        <el-button
          size="mini"
          type="warning"
          :disabled="!cachedSnapshots.length"
          @click="handleClearSnapshots"
        >
          清空缓存
        </el-button>
      </div>

      <div class="dashboard-page__toolbar-group">
        <el-select
          v-model="selectedProfileIds"
          multiple
          placeholder="选择需要刷新的仓库配置"
          class="dashboard-page__profile-select"
          size="mini"
        >
          <el-option
            v-for="profile in repoProfiles"
            :key="profile.id"
            :label="profile.name"
            :value="profile.id"
          />
        </el-select>
        <el-button
          type="primary"
          size="mini"
          :disabled="!selectedProfileIds.length"
          :loading="loadingMatrix"
          @click="handleRefresh"
        >
          刷新
        </el-button>
        <el-button size="mini" @click="triggerImport">
          导入配置
        </el-button>
        <el-button
          size="mini"
          :disabled="!repoProfiles.length"
          :loading="exportingProfiles"
          @click="handleExportProfiles"
        >
          导出配置
        </el-button>
        <el-button
          size="mini"
          type="success"
          :loading="syncingDefaults"
          @click="handleSyncDefaults"
        >
          同步默认配置
        </el-button>
        <el-button
          size="mini"
          type="danger"
          :disabled="!selectedProfileIds.length"
          @click="handleDeleteProfiles"
        >
          删除配置
        </el-button>
                <el-button
          size="mini"
          type="primary"
          plain
          @click="openMultiExportDialog"
        >
          导出多仓报表
        </el-button>
        <input
          ref="fileInput"
          type="file"
          accept="application/json"
          class="dashboard-page__file-input"
          @change="handleImportFile"
        />
      </div>
      <div
        v-if="profileBundleDescription"
        class="dashboard-page__bundle-info"
      >
        {{ profileBundleDescription }}
      </div>
    </div>

    <el-alert
      v-if="!cachedSnapshots.length"
      title="暂无缓存，请导入配置并点击刷新执行一次全量扫描"
      type="info"
      :closable="false"
      class="dashboard-page__hint"
    />

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
          :available-extensions="availableFileExtensions"
          :original-data="diffMatrix"
          @change="handleFilterChange"
          @reset="handleFilterReset"
        />
      </template>
    </diff-matrix>
    <multi-repo-export-dialog
      :visible.sync="showMultiExportDialog"
    />
  </div>
</template>

<script>
import { mapState, mapActions, mapMutations, mapGetters } from 'vuex';
import { downloadBlob } from '../utils/download';
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
      selectedProfileIds: [],
      activeSnapshotKey: '',
      syncingDefaults: false,
      exportingProfiles: false,
      showMultiExportDialog: false,
    };
  },
  computed: {
    ...mapState('diff', [
      'taskId',
      'summary',
      'loadingMatrix',
      'overallCoverage',
      'matrixFilters',
      'repoProfiles',
      'cachedSnapshots',
      'loadingSnapshots',
      'activeRepoId',
      'profileBundleMeta',
      'diffMatrix',
    ]),
    ...mapGetters('diff', {
      displayedDiffMatrix: 'filteredDiffMatrix',
      availableFileExtensions: 'availableFileExtensions',
    }),
    profileBundleDescription() {
      const meta = this.profileBundleMeta || {};
      const version = meta.version ? `版本 ${meta.version}` : '';
      const timestamp = meta.generatedAt ? `更新时间 ${this.formatDateTime(meta.generatedAt)}` : '';
      return [version, timestamp].filter(Boolean).join(' · ');
    },
  },
  watch: {
    activeRepoId: {
      immediate: true,
      handler(value) {
        this.activeSnapshotKey = value || '';
      },
    },
    repoProfiles: {
      immediate: true,
      handler(next) {
        if (!this.selectedProfileIds.length && Array.isArray(next) && next.length) {
          this.selectedProfileIds = next.map((item) => item.id);
        }
      },
    },
  },
  created() {
    this.initialize();
  },
  methods: {
    ...mapActions('diff', [
      'loadProfiles',
      'importProfiles',
      'removeProfiles',
      'scanProfiles',
      'loadSnapshots',
      'applySnapshot',
      'clearSnapshots',
      'syncDefaultProfiles',
      'exportProfiles',
    ]),
    ...mapMutations('diff', ['setMatrixFilters', 'resetMatrixFilters']),
    async initialize() {
      await this.loadProfiles({ bootstrapDefaults: true });
      await this.reloadSnapshots(true);
    },
    snapshotKey(snapshot) {
      if (!snapshot) {
        return '';
      }
      return snapshot.repoId || snapshot.taskId || '';
    },
    formatSnapshotLabel(snapshot) {
      if (!snapshot) {
        return '未知';
      }
      const name = snapshot.repoName || snapshot.response?.summary?.repoName || snapshot.repoId;
      const task = snapshot.taskId ? snapshot.taskId.slice(0, 8) : '';
      return task ? `${name || '未命名'} (${task})` : (name || '未命名');
    },
    async reloadSnapshots(autoApply = false) {
      await this.loadSnapshots({
        autoApply,
        preferredRepoId: this.activeSnapshotKey,
      });
    },
    handleSnapshotChange(repoId) {
      const target = this.cachedSnapshots.find(
        (item) => this.snapshotKey(item) === repoId,
      );
      if (target) {
        this.applySnapshot(target);
      }
    },
    triggerImport() {
      if (this.$refs.fileInput) {
        this.$refs.fileInput.value = '';
        this.$refs.fileInput.click();
      }
    },
    handleImportFile(event) {
      const file = event.target.files && event.target.files[0];
      if (!file) {
        return;
      }
      const reader = new FileReader();
      reader.onload = async () => {
        try {
          const payload = JSON.parse(reader.result);
          const profiles = await this.importProfiles(payload);
          this.selectedProfileIds = profiles.map((profile) => profile.id);
          this.$message.success(`成功导入 ${profiles.length} 条配置`);
        } catch (error) {
          this.$message.error(error.message || '配置文件格式有误');
        }
      };
      reader.readAsText(file);
    },
    async handleDeleteProfiles() {
      if (!this.selectedProfileIds.length) {
        return;
      }
      try {
        await this.$confirm('确定删除选中的仓库配置吗？', '提示', {
          type: 'warning',
        });
        await this.removeProfiles(this.selectedProfileIds);
        this.selectedProfileIds = [];
        this.$message.success('已删除配置');
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error(error.message || '删除失败');
        }
      }
    },
    async handleRefresh() {
      if (!this.selectedProfileIds.length) {
        this.$message.warning('请选择需要刷新的仓库配置');
        return;
      }
      try {
        await this.scanProfiles({ profileIds: this.selectedProfileIds });
        await this.reloadSnapshots(true);
        this.$message.success('全量扫描完成');
      } catch (error) {
        this.$message.error(error.message || '刷新失败');
      }
    },
    async handleClearSnapshots() {
      if (!this.cachedSnapshots.length) {
        return;
      }
      try {
        await this.$confirm('清空缓存后需要重新全量扫描，确定继续吗？', '提示', {
          type: 'warning',
        });
        await this.clearSnapshots();
        this.activeSnapshotKey = '';
        this.$message.success('缓存已清空');
      } catch (error) {
        if (error !== 'cancel') {
          this.$message.error(error.message || '清空失败');
        }
      }
    },
    async handleSyncDefaults() {
      this.syncingDefaults = true;
      try {
        const profiles = await this.syncDefaultProfiles();
        if (profiles.length) {
          this.selectedProfileIds = profiles.map((profile) => profile.id);
        }
        this.$message.success('已同步默认配置');
      } catch (error) {
        this.$message.error(error.message || '同步失败');
      } finally {
        this.syncingDefaults = false;
      }
    },
    async handleExportProfiles() {
      if (!this.repoProfiles.length) {
        this.$message.warning('暂无配置可导出');
        return;
      }
      this.exportingProfiles = true;
      try {
        const bundle = await this.exportProfiles();
        const content = JSON.stringify(bundle, null, 2);
        const filename = `repo-profiles-${bundle.version || Date.now()}.json`;
        const blob = new Blob([content], { type: 'application/json;charset=utf-8' });
        downloadBlob(blob, filename);
        this.$message.success('配置已导出');
      } catch (error) {
        this.$message.error(error.message || '导出失败');
      } finally {
        this.exportingProfiles = false;
      }
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
    openMultiExportDialog() {
      this.showMultiExportDialog = true;
    },
    formatDateTime(value) {
      if (!value) {
        return '';
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
.dashboard-page__toolbar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.dashboard-page__toolbar-group {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.dashboard-page__profile-select {
  min-width: 280px;
}

.dashboard-page__snapshot-select {
  min-width: 220px;
}

.dashboard-page__file-input {
  display: none;
}

.dashboard-page__bundle-info {
  font-size: 12px;
  color: #909399;
}

.dashboard-page__hint {
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
