<template>
  <div class="dashboard-page">
    <dashboard-header :project-paths="projectPaths">
      <template #actions>
        <el-button
          type="primary"
          icon="el-icon-refresh"
          :loading="listLoading && !reloadLoading"
          :disabled="reloadLoading"
          @click="refreshAll"
        >
          刷新数据
        </el-button>
        <el-button
          type="warning"
          icon="el-icon-refresh-right"
          :loading="reloadLoading"
          :disabled="reloadLoading"
          @click="reloadFromSource"
        >
          重新加载
        </el-button>
        <el-button
          type="success"
          icon="el-icon-link"
          @click="goToGitComparison"
        >
          Git 比对
        </el-button>
      </template>
    </dashboard-header>

    <el-row :gutter="16" class="metrics-row">
      <el-col :span="12">
        <category-donut :stats="overview?.codeCategoryStats || []" />
      </el-col>
      <el-col :span="12">
        <new-code-ratio-card
          :ratio="overview?.newCodeRatio || 0"
          :total-lines="overview?.totalLines || 0"
        />
      </el-col>
    </el-row>

    <batch-toolbar
      v-model="selectedCategories"
      :selected-ids="selectedIds"
      :category-options="categoryOptions"
      :file-name="fileNameFilter"
      :loading="toolbarLoading"
      @generate="handleGenerate"
      @apply="handleApply"
      @undo="handleUndo"
      @ignore="handleIgnore"
      @filter-change="handleFilterChange"
      @file-name-change="handleFileNameChange"
    />

    <code-block-table
      :code-blocks="codeBlocks"
      :pagination="pagination"
      :loading="listLoading"
      :selected-ids="selectedIds"
      :category-options="categoryOptions"
      @selection-change="handleSelectionChange"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
      @view-detail="handleViewDetail"
    />
  </div>
</template>

<script>
import { mapState, mapGetters } from 'vuex';
import DashboardHeader from '@/components/dashboard/DashboardHeader.vue';
import CategoryDonut from '@/components/dashboard/CategoryDonut.vue';
import NewCodeRatioCard from '@/components/dashboard/NewCodeRatioCard.vue';
import BatchToolbar from '@/components/dashboard/BatchToolbar.vue';
import CodeBlockTable from '@/views/dashboard/CodeBlockTable.vue';
import { formatDateTime } from '@/utils/date';

export default {
  name: 'Dashboard',
  components: {
    DashboardHeader,
    CategoryDonut,
    NewCodeRatioCard,
    BatchToolbar,
    CodeBlockTable,
  },
  data() {
    return {
      toolbarLoading: false,
      reloadLoading: false,
    };
  },
  computed: {
    ...mapState('migration', {
      overview: (state) => state.overview,
      codeBlocks: (state) => state.codeBlocks,
      pagination: (state) => state.pagination,
      listLoading: (state) => state.loading,
      categoryFilterState: (state) => state.filters.categories,
      selectedIds: (state) => state.selectedIds,
      fileNameFilterState: (state) => state.filters.fileName,
    }),
    ...mapGetters('migration', ['categoryOptions']),
    selectedCategories: {
      get() {
        return this.categoryFilterState;
      },
      set(value) {
        this.$store.commit('migration/SET_FILTERS', {
          categories: value,
          excludeCategories: [],
        });
      },
    },
    fileNameFilter() {
      return this.fileNameFilterState;
    },
    projectPaths() {
      if (!this.overview) {
        return null;
      }
      return {
        old: this.overview.oldProjectPath,
        new: this.overview.newProjectPath,
        lastSyncedAt: this.overview.lastSyncedAt
          ? formatDateTime(this.overview.lastSyncedAt)
          : null,
      };
    },
  },
  created() {
    // 页面初始化后立刻拉取列表数据
    this.refreshAll();
  },
  methods: {
    async refreshAll(options = {}) {
      if (this.reloadLoading) {
        return;
      }
      const { resetPage = false } = options;
      await this.$store.dispatch('migration/initDashboard', { resetPage });
    },
    async reloadFromSource() {
      if (this.reloadLoading) {
        return;
      }
      try {
        await this.$confirm(
          '该操作将触发一次全量重新扫描，耗时可能较长，确定继续？',
          '重新加载确认',
          {
            type: 'warning',
            confirmButtonText: '确认重新加载',
            cancelButtonText: '取消',
          }
        );
      } catch (error) {
        return;
      }
      this.reloadLoading = true;
      try {
        await this.$store.dispatch('migration/initDashboard', {
          refresh: true,
          resetPage: true,
        });
        this.$message.success('全量重新加载完成');
      } finally {
        this.reloadLoading = false;
      }
    },
    // 统一封装批量操作流程，带上 loading 与回调
    async executeBatch(action, payload, options = {}) {
      const preserveSelection = options.preserveSelection ?? false;
      if (!payload?.ids?.length) {
        payload?.done?.();
        return;
      }
      this.toolbarLoading = true;
      try {
        await this.$store.dispatch('migration/performBatchAction', {
          type: action,
          ids: payload.ids,
          preserveSelection,
        });
      } finally {
        this.toolbarLoading = false;
        payload?.done?.();
      }
    },
    handleGenerate(payload) {
      this.executeBatch('generate', payload, { preserveSelection: true });
    },
    handleApply(payload) {
      this.executeBatch('apply', payload);
    },
    handleUndo(payload) {
      this.executeBatch('undo', payload);
    },
    handleIgnore(payload) {
      this.executeBatch('ignore', payload);
    },
    handleFilterChange(categoryKeys) {
      // 更新分类筛选后重置分页
      this.$store.dispatch('migration/fetchCodeBlocks', {
        categories: categoryKeys,
        excludeCategories: [],
        resetPage: true,
      });
    },
    handleSelectionChange(ids) {
      this.$store.dispatch('migration/setSelectedIds', ids);
    },
    handlePageChange(page) {
      this.$store.dispatch('migration/fetchCodeBlocks', { page });
    },
    handleSizeChange(size) {
      this.$store.dispatch('migration/fetchCodeBlocks', {
        size,
        page: 1,
      });
    },
    handleFileNameChange(keyword) {
      this.$store.dispatch('migration/fetchCodeBlocks', {
        fileName: keyword,
        resetPage: true,
      });
    },
    handleViewDetail(id) {
      this.$router.push({ name: 'CodeBlockDetail', params: { id } });
    },
    goToGitComparison() {
      const sourcePath = this.overview?.oldProjectPath || '';
      const targetPath = this.overview?.newProjectPath || '';
      const sourceKey = this.extractProjectKey(sourcePath);
      const targetKey = this.resolveTargetKey(
        sourceKey,
        this.extractProjectKey(targetPath)
      );
      if (sourceKey) {
        this.$store.dispatch('gitComparison/setSourceProjectKey', sourceKey);
      }
      if (targetKey) {
        this.$store.dispatch('gitComparison/setTargetProjectKey', targetKey);
      }
      const query = {};
      if (sourceKey) {
        query.source = sourceKey;
      }
      if (targetKey) {
        query.target = targetKey;
      }
      const route = { name: 'GitComparison' };
      if (Object.keys(query).length) {
        route.query = query;
      }
      this.$router.push(route);
    },
    extractProjectKey(path) {
      if (!path || typeof path !== 'string') {
        return '';
      }
      const segments = path
        .split(/[/\\]+/)
        .map((item) => item.trim())
        .filter(Boolean);
      if (!segments.length) {
        return '';
      }
      return segments[segments.length - 1];
    },
    resolveTargetKey(sourceKey, candidate) {
      const normalizedSource = (sourceKey || '').trim().toLowerCase();
      const normalizedCandidate = (candidate || '').trim().toLowerCase();
      if (normalizedCandidate && normalizedCandidate !== normalizedSource) {
        return candidate;
      }
      const existing = this.$store?.state?.gitComparison?.targetProjectKey;
      if (existing && existing.trim().toLowerCase() !== normalizedSource) {
        return existing;
      }
      return '';
    },
  },
};
</script>

<style scoped lang="scss">
.dashboard-page {
  padding: 24px;
}

.metrics-row {
  margin-bottom: 16px;
}
</style>

