<template>
  <div class="detail-page">
    <detail-header
      :file-path="detail?.filePath"
      :line-range="lineRange"
      :categories="headerCategories"
      :previous-id="detail?.previousId"
      :next-id="detail?.nextId"
      @back="handleBack"
      @previous="handlePrevious"
      @next="handleNext"
    />

    <action-bar
      :status="detail?.status"
      :loading="actionLoading"
      @generate="handleGenerate"
      @undo="handleUndo"
      @apply="handleApply"
      @ignore="handleIgnore"
    />

    <el-card shadow="never" class="diff-card" v-loading="detailLoading">
      <code-diff-viewer
        v-if="detail && !isIncremental"
        :original="detail.oldCode || ''"
        :modified="detail.newCode || ''"
        :language="detectLanguage(detail.filePath)"
      />
      <incremental-diff-viewer
        v-else-if="detail"
        :segments="diffSegments"
      />
      <div v-else class="empty-state">
        <el-empty description="暂无数据" />
      </div>
    </el-card>

    <AISuggestionPanel
      :enabled="aiEnabled"
      :suggestion="aiSuggestion"
      :loading="aiLoading"
    />
  </div>
</template>

<script>
import { mapState, mapGetters } from 'vuex';
import DetailHeader from '@/components/detail/DetailHeader.vue';
import ActionBar from '@/components/detail/ActionBar.vue';
import CodeDiffViewer from '@/components/detail/CodeDiffViewer.vue';
import IncrementalDiffViewer from '@/components/detail/IncrementalDiffViewer.vue';
import AISuggestionPanel from '@/components/detail/AISuggestionPanel.vue';
import featureFlags from '@/config/featureFlags';

export default {
  name: 'CodeBlockDetail',
  components: {
    DetailHeader,
    ActionBar,
    CodeDiffViewer,
    IncrementalDiffViewer,
    AISuggestionPanel,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      actionLoading: false,
    };
  },
  computed: {
    ...mapState('migration', {
      detail: (state) => state.detail,
      detailLoading: (state) => state.detailLoading,
      aiSuggestion: (state) => state.aiSuggestion,
      aiLoading: (state) => state.aiLoading,
    }),
    ...mapGetters('migration', ['categoryOptions']),
    lineRange() {
      if (!this.detail) {
        return '';
      }
      return `L${this.detail.startLine}-${this.detail.endLine}`;
    },
    headerCategories() {
      if (!this.detail) {
        return [];
      }
      return this.resolveCategories(this.detail);
    },
    aiEnabled() {
      if (!featureFlags.enableAI) {
        return false;
      }
      return this.detail?.aiSuggestionEnabled ?? false;
    },
    diffMode() {
      return (this.detail?.diffMode || 'full').toLowerCase();
    },
    isIncremental() {
      return this.diffMode === 'incremental';
    },
    diffSegments() {
      if (!Array.isArray(this.detail?.diffSegments)) {
        return [];
      }
      return this.detail.diffSegments;
    },
  },
  watch: {
    id: {
      handler() {
        this.loadData();
      },
      immediate: true,
    },
  },
  methods: {
    async loadData() {
      const detail = await this.$store.dispatch(
        'migration/fetchDetail',
        this.id
      );
      if (featureFlags.enableAI && detail?.aiSuggestionEnabled) {
        await this.$store.dispatch('migration/fetchAISuggestion', this.id);
      }
    },
    handleBack() {
      this.$router.push({ name: 'Dashboard' });
    },
    async executeDetailAction(type, payload) {
      this.actionLoading = true;
      try {
        await this.$store.dispatch('migration/performDetailAction', {
          id: this.id,
          type,
        });
      } finally {
        this.actionLoading = false;
        payload?.done?.();
      }
    },
    handleGenerate(payload) {
      this.executeDetailAction('generate', payload);
    },
    handleUndo(payload) {
      this.executeDetailAction('undo', payload);
    },
    handleApply(payload) {
      this.executeDetailAction('apply', payload);
    },
    handleIgnore(payload) {
      this.executeDetailAction('ignore', payload);
    },
    handlePrevious() {
      const target = this.detail?.previousId;
      this.navigateToDetail(target);
    },
    handleNext() {
      const target = this.detail?.nextId;
      this.navigateToDetail(target);
    },
    navigateToDetail(targetId) {
      if (!targetId || targetId === this.id) {
        return;
      }
      this.$router.push({ name: 'CodeBlockDetail', params: { id: targetId } });
    },
    detectLanguage(filePath) {
      if (!filePath) {
        return 'javascript';
      }
      if (filePath.endsWith('.ts') || filePath.endsWith('.tsx')) {
        return 'typescript';
      }
      if (filePath.endsWith('.java')) {
        return 'java';
      }
      if (filePath.endsWith('.py')) {
        return 'python';
      }
      if (filePath.endsWith('.vue')) {
        return 'vue';
      }
      return 'javascript';
    },
    resolveCategories(row) {
      const lookup = (this.categoryOptions || []).reduce((acc, item) => {
        acc[item.value] = item;
        return acc;
      }, {});
      const source = this.normalizeCategoryInput(row);
      const resolved = source
        .map((entry) => {
          if (!entry) {
            return null;
          }
          if (typeof entry === 'string') {
            const matched = lookup[entry];
            if (matched) {
              return matched;
            }
            return {
              value: entry,
              label: entry,
              color: row.categoryColor || '#9ca3af',
            };
          }
          if (typeof entry === 'object') {
            const key = entry.key || entry.value || entry.label;
            const matched = key ? lookup[key] : null;
            return {
              value: key || entry.label,
              label: entry.label || matched?.label || key || '',
              color: entry.color || matched?.color || '#9ca3af',
            };
          }
          return null;
        })
        .filter((item) => item && item.label);
      if (resolved.length > 0) {
        return resolved;
      }
      if (row.categoryLabel) {
        return [
          {
            value: row.categoryKey || row.categoryLabel,
            label: row.categoryLabel,
            color: row.categoryColor || '#9ca3af',
          },
        ];
      }
      if (row.statusLabel) {
        return [
          {
            value: row.status || row.statusLabel,
            label: row.statusLabel,
            color: row.statusColor || '#9ca3af',
          },
        ];
      }
      return [];
    },
    normalizeCategoryInput(row) {
      if (Array.isArray(row.categories)) {
        return row.categories;
      }
      if (Array.isArray(row.categoryKeys)) {
        return row.categoryKeys;
      }
      if (Array.isArray(row.categoryLabels)) {
        return row.categoryLabels;
      }
      if (row.categoryKey) {
        return [row.categoryKey];
      }
      if (row.category) {
        return [row.category];
      }
      return [];
    },
  },
};
</script>

<style scoped lang="scss">
.detail-page {
  padding: 24px;
}

.diff-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
}

.empty-state {
  padding: 80px 0;
}
</style>
