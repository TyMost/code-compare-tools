<template>
  <div class="incremental-diff-view">
    <el-card class="filter-card" shadow="never">
      <div class="filters">
        <el-input
          v-model="localProjectKey"
          size="small"
          placeholder="项目标识（可选）"
          class="filter-input"
          clearable
        />
        <el-button
          type="primary"
          size="small"
          :loading="overviewLoading"
          icon="el-icon-search"
          @click="handleFetch"
        >查询</el-button>
        <el-button
          size="small"
          :loading="overviewLoading"
          icon="el-icon-refresh"
          @click="handleRefresh"
        >刷新数据</el-button>
        <span class="meta" v-if="overview && overview.generatedAt">
          数据最后更新：{{ formatDate(overview.generatedAt) }}
        </span>
      </div>
    </el-card>

    <el-card v-if="overview" class="context-card" shadow="never">
      <div class="context-grid">
        <div
          class="context-item"
          v-for="item in comparisonContext"
          :key="item.key"
        >
          <div class="context-label">{{ item.label }}</div>
          <div class="context-path" :title="item.path || '-'">
            {{ item.path || '-' }}
          </div>
          <div class="context-branch">
            <span>基线：{{ item.base || '-' }}</span>
            <span>目标：{{ item.latest || '-' }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" class="content-row">
      <el-col :span="16">
        <el-card class="file-list-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>增量文件</span>
              <el-tag v-if="overview" size="mini" type="info">
                文件数：{{ files.length }}
              </el-tag>
            </div>
          </template>
          <el-skeleton :rows="8" animated v-if="overviewLoading && !files.length" />
          <el-table
            v-else
            :data="files"
            :row-key="row => row.filePath"
            :expand-row-keys="expandedKeys"
            @expand-change="handleExpandChange"
            height="500"
            stripe
          >
            <el-table-column type="expand">
              <template #default="scope">
                <div class="file-expand-panel">
                  <el-skeleton v-if="isDetailLoading(scope.row.filePath)" :rows="6" animated />
                  <div v-else-if="!fileDetails[scope.row.filePath]" class="empty-placeholder">
                    <el-empty description="未获取到文件详情" />
                  </div>
                  <div v-else>
                    <div class="file-summary">
                      <div class="file-summary-path">{{ scope.row.filePath }}</div>
                      <div class="file-summary-meta">
                        <span>相似度：{{ formatPercent(fileDetails[scope.row.filePath].averageSimilarity) }}</span>
                        <span>块数：{{ fileDetails[scope.row.filePath].totalBlocks }}</span>
                        <span>待处理：{{ fileDetails[scope.row.filePath].unlabeledBlocks }}</span>
                      </div>
                    </div>
                    <div
                      v-if="hasGitDiff(fileDetails[scope.row.filePath])"
                      class="git-diff-table"
                    >
                      <div
                        v-for="row in buildGitDiffRows(fileDetails[scope.row.filePath].gitDiff)"
                        :key="row.key"
                        class="diff-row"
                        :class="row.type"
                      >
                        <div class="diff-cell line-no">{{ row.leftNo }}</div>
                        <div class="diff-cell line-content">{{ row.leftText }}</div>
                        <div class="diff-cell line-no">{{ row.rightNo }}</div>
                        <div class="diff-cell line-content">{{ row.rightText }}</div>
                      </div>
                    </div>
                    <div v-else class="git-diff-fallback">
                      <el-alert
                        title="未获取到 Git hunk 数据，展示回退的差异片段"
                        type="info"
                        :closable="false"
                        show-icon
                      />
                      <pre class="fallback-snippet">{{ formatFallbackDiff(fileDetails[scope.row.filePath]) }}</pre>
                    </div>

                    <div
                      v-if="fileDetails[scope.row.filePath].blocks?.length"
                      class="block-section"
                    >
                      <div
                        v-for="(block, index) in fileDetails[scope.row.filePath].blocks"
                        :key="block.blockId || index"
                        class="block-item"
                      >
                        <div class="block-header">
                          <span class="block-title">块 {{ index + 1 }}</span>
                          <span class="block-meta">
                            状态：{{ block.status || '-' }} · 风险：{{ block.riskLevel || '-' }} · 相似度：{{ formatPercent(block.diff?.similarityScore) }}
                          </span>
                        </div>
                        <div class="block-diff-table">
                          <div
                            v-for="row in buildBlockRows(block)"
                            :key="row.key"
                            class="diff-row"
                            :class="row.type"
                          >
                            <div class="diff-cell line-no">{{ row.leftNo }}</div>
                            <div class="diff-cell line-content">{{ row.leftText }}</div>
                            <div class="diff-cell line-no">{{ row.rightNo }}</div>
                            <div class="diff-cell line-content">{{ row.rightText }}</div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <div v-else class="block-empty">
                      <el-empty description="暂无块级差异" />
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="filePath" label="文件" min-width="240" />
            <el-table-column
              prop="averageSimilarity"
              label="相似度"
              width="120"
              :formatter="(_, __, value) => formatPercent(value)"
            />
            <el-table-column prop="totalBlocks" label="块数" width="90" />
            <el-table-column prop="unlabeledBlocks" label="需处理" width="100" />
            <el-table-column
              prop="generatedAt"
              label="生成时间"
              width="180"
              :formatter="(_, __, value) => formatDate(value)"
            />
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card class="ranking-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>相似度排名（低→高）</span>
            </div>
          </template>
          <el-skeleton :rows="5" animated v-if="rankingLoading && !ranking.length" />
          <el-empty v-else-if="!ranking.length" description="暂无排名数据" />
          <el-table v-else :data="ranking" height="500" size="small" border>
            <el-table-column prop="rank" label="#" width="60" align="center" />
            <el-table-column prop="filePath" label="文件" min-width="180" />
            <el-table-column
              prop="averageSimilarity"
              label="相似度"
              width="120"
              align="center"
              :formatter="(_, __, value) => formatPercent(value)"
            />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import { mapState, mapGetters, mapActions } from 'vuex';
import dayjs from 'dayjs';

export default {
  name: 'IncrementalDiffView',
  data() {
    return {
      localProjectKey: '',
      expandedKeys: [],
      loadingDetails: {},
    };
  },
  computed: {
    ...mapState('incrementalDiff', [
      'overview',
      'overviewLoading',
      'ranking',
      'rankingLoading',
      'projectKey',
      'lastUpdatedAt',
      'details',
    ]),
    ...mapGetters('incrementalDiff', ['files']),
    fileDetails() {
      return this.details || {};
    },
    comparisonContext() {
      if (!this.overview) {
        return [];
      }
      const roots = this.overview.projectRoots || {};
      const base = this.overview.baseCommits || {};
      const latest = this.overview.latestCommits || {};
      const keys = new Set([
        ...Object.keys(roots),
        ...Object.keys(base),
        ...Object.keys(latest),
      ]);
      if (keys.size === 0) {
        if (this.overview.projectCode) {
          keys.add(this.overview.projectCode);
        }
      }
      return Array.from(keys).map((key) => ({
        key,
        label: this.resolveContextLabel(key),
        path: roots[key] || '',
        base: base[key] || '',
        latest: latest[key] || '',
      }));
    },
  },
  watch: {
    projectKey: {
      immediate: true,
      handler(value) {
        this.localProjectKey = value || '';
      },
    },
    overview() {
      this.expandedKeys = [];
      this.loadingDetails = {};
    },
  },
  created() {
    this.bootstrap();
  },
  methods: {
    ...mapActions('incrementalDiff', [
      'setProjectKey',
      'fetchOverview',
      'fetchFileDetail',
      'fetchRanking',
    ]),
    async bootstrap() {
      await this.fetchOverview({ refresh: true });
      await this.fetchRanking();
    },
    async handleFetch() {
      this.setProjectKey((this.localProjectKey || '').trim());
      this.expandedKeys = [];
      this.loadingDetails = {};
      await this.fetchOverview({ refresh: true });
      await this.fetchRanking();
    },
    async handleRefresh() {
      this.setProjectKey((this.localProjectKey || '').trim());
      this.expandedKeys = [];
      this.loadingDetails = {};
      await this.fetchOverview({ refresh: true });
      await this.fetchRanking();
    },
    async handleExpandChange(row, expandedRows) {
      this.expandedKeys = expandedRows.map((item) => item.filePath);
      if (this.expandedKeys.includes(row.filePath)) {
        await this.ensureFileDetail(row.filePath);
      }
    },
    async ensureFileDetail(path) {
      if (!path) {
        return;
      }
      if (this.fileDetails[path]) {
        return;
      }
      if (this.loadingDetails[path]) {
        return;
      }
      this.$set(this.loadingDetails, path, true);
      try {
        await this.fetchFileDetail(path);
      } finally {
        this.$set(this.loadingDetails, path, false);
      }
    },
    isDetailLoading(path) {
      return !!this.loadingDetails[path];
    },
    hasGitDiff(detail) {
      return detail
        && detail.gitDiff
        && Array.isArray(detail.gitDiff.hunks)
        && detail.gitDiff.hunks.length > 0;
    },
    buildGitDiffRows(gitDiff) {
      const rows = [];
      if (!gitDiff || !Array.isArray(gitDiff.hunks)) {
        return rows;
      }
      gitDiff.hunks.forEach((hunk, hunkIndex) => {
        const oldCount = hunk.oldLineCount || 0;
        const newCount = hunk.newLineCount || 0;
        rows.push({
          key: `h-${hunkIndex}`,
          type: 'hunk',
          leftNo: `-${hunk.oldStartLine || 0},${oldCount}`,
          leftText: `@@ -${hunk.oldStartLine || 0},${oldCount} @@`,
          rightNo: `+${hunk.newStartLine || 0},${newCount}`,
          rightText: `@@ +${hunk.newStartLine || 0},${newCount} @@`,
        });
        let leftLine = Math.max(1, hunk.oldStartLine || 1);
        let rightLine = Math.max(1, hunk.newStartLine || 1);
        (hunk.lines || []).forEach((line, lineIndex) => {
          const raw = line || '';
          const marker = raw.charAt(0);
          const text = raw.length > 1 ? raw.slice(1) : '';
          if (marker === ' ') {
            rows.push({
              key: `c-${hunkIndex}-${lineIndex}`,
              type: 'context',
              leftNo: leftLine,
              leftText: text,
              rightNo: rightLine,
              rightText: text,
            });
            leftLine += 1;
            rightLine += 1;
          } else if (marker === '-') {
            rows.push({
              key: `d-${hunkIndex}-${lineIndex}`,
              type: 'remove',
              leftNo: leftLine,
              leftText: text,
              rightNo: '',
              rightText: '',
            });
            leftLine += 1;
          } else if (marker === '+') {
            rows.push({
              key: `a-${hunkIndex}-${lineIndex}`,
              type: 'add',
              leftNo: '',
              leftText: '',
              rightNo: rightLine,
              rightText: text,
            });
            rightLine += 1;
          } else if (marker === '\\') {
            const message = raw.length > 1 ? raw.slice(1).trim() : raw;
            rows.push({
              key: `i-${hunkIndex}-${lineIndex}`,
              type: 'info',
              leftNo: '',
              leftText: message,
              rightNo: '',
              rightText: message,
            });
          } else {
            rows.push({
              key: `u-${hunkIndex}-${lineIndex}`,
              type: 'context',
              leftNo: leftLine,
              leftText: raw,
              rightNo: rightLine,
              rightText: raw,
            });
            leftLine += 1;
            rightLine += 1;
          }
        });
      });
      return rows;
    },
    buildBlockRows(block) {
      const rows = [];
      if (!block || !block.diff) {
        return rows;
      }
      const sourceLines = block.diff.sourceLines || [];
      const targetLines = block.diff.targetLines || [];
      const length = Math.max(sourceLines.length, targetLines.length);
      let leftLine = Math.max(1, block.diff.sourceStartLine || 1);
      let rightLine = Math.max(1, block.diff.targetStartLine || 1);
      for (let i = 0; i < length; i += 1) {
        const leftText = sourceLines[i] ?? '';
        const rightText = targetLines[i] ?? '';
        let type = 'context';
        if (leftText !== rightText) {
          if (leftText && rightText) {
            type = 'change';
          } else if (leftText) {
            type = 'remove';
          } else {
            type = 'add';
          }
        }
        rows.push({
          key: `${block.blockId || i}-${i}`,
          type,
          leftNo: leftText ? leftLine : '',
          leftText,
          rightNo: rightText ? rightLine : '',
          rightText,
        });
        if (leftText) {
          leftLine += 1;
        }
        if (rightText) {
          rightLine += 1;
        }
      }
      return rows;
    },
    formatFallbackDiff(detail) {
      if (!detail || !detail.blocks) {
        return '未检测到差异片段';
      }
      return detail.blocks
        .map((block, index) => {
          const source = (block.diff?.sourceContent || '').trim();
          const target = (block.diff?.targetContent || '').trim();
          return `# 块 ${index + 1}\nSOURCE:\n${source || '-'}\n\nTARGET:\n${target || '-'}`;
        })
        .join('\n\n');
    },
    formatPercent(value) {
      if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return '-';
      }
      return `${Number(value).toFixed(1)}%`;
    },
    formatDate(value) {
      if (!value) {
        return '-';
      }
      return dayjs(value).format('YYYY-MM-DD HH:mm');
    },
    resolveContextLabel(key) {
      const normalized = (key || '').toUpperCase();
      if (normalized === 'SOURCE') {
        return '源目录';
      }
      if (normalized === 'TARGET') {
        return '目标目录';
      }
      return key || '目录';
    },
  },
};
</script>

<style lang="scss" scoped>
.incremental-diff-view {
  padding: 16px;

  .filter-card {
    margin-bottom: 16px;

    .filters {
      display: flex;
      align-items: center;
      gap: 8px;

      .filter-input {
        width: 220px;
      }

      .meta {
        margin-left: auto;
        color: #909399;
        font-size: 12px;
      }
    }
  }

  .context-card {
    margin-bottom: 16px;

    .context-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
    }

    .context-item {
      flex: 1 1 220px;
      min-width: 220px;
      padding: 12px;
      border: 1px solid #ebeef5;
      border-radius: 6px;
      background: #f8f9fb;

      .context-label {
        font-weight: 600;
        margin-bottom: 4px;
        color: #303133;
      }

      .context-path {
        font-size: 12px;
        color: #606266;
        margin-bottom: 6px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .context-branch {
        display: flex;
        flex-direction: column;
        gap: 2px;
        font-size: 12px;
        color: #909399;
      }
    }
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
  }

  .file-expand-panel {
    padding: 12px 0;
  }

  .file-summary {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;

    .file-summary-path {
      font-weight: 600;
      color: #303133;
      word-break: break-all;
    }

    .file-summary-meta {
      display: flex;
      gap: 12px;
      font-size: 12px;
      color: #606266;
    }
  }

  .git-diff-table,
  .block-diff-table {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    overflow: hidden;
    margin-bottom: 16px;
  }

  .diff-row {
    display: flex;
    font-family: 'Fira Code', 'Courier New', monospace;
    font-size: 12px;
    border-bottom: 1px solid #ebeef5;

    &:last-child {
      border-bottom: none;
    }

    &.context {
      background: #fff;
    }

    &.add {
      background: #f0f9eb;
      color: #2f7e0b;
    }

    &.remove {
      background: #fef0f0;
      color: #c0392b;
    }

    &.change {
      background: #fff8e6;
      color: #b26a00;
    }

    &.info {
      background: #f5f7fa;
      color: #909399;
      font-style: italic;
    }

    &.hunk {
      background: #ecf5ff;
      color: #409eff;
      font-weight: 600;
    }
  }

  .diff-cell {
    padding: 4px 8px;
    white-space: pre;
    word-break: break-word;

    &.line-no {
      width: 60px;
      text-align: right;
      color: #909399;
      background: #f5f7fa;
      border-right: 1px solid #ebeef5;
    }

    &.line-content {
      flex: 1;
    }
  }

  .git-diff-fallback {
    margin-bottom: 16px;

    .fallback-snippet {
      margin-top: 12px;
      background: #f5f7fa;
      padding: 12px;
      border-radius: 4px;
      font-family: 'Fira Code', 'Courier New', monospace;
      font-size: 12px;
      white-space: pre-wrap;
    }
  }

  .block-section {
    display: flex;
    flex-direction: column;
    gap: 16px;

    .block-item {
      border: 1px solid #ebeef5;
      border-radius: 4px;
      padding: 12px;
      background: #fff;
    }

    .block-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;

      .block-title {
        font-weight: 600;
        color: #303133;
      }

      .block-meta {
        font-size: 12px;
        color: #606266;
      }
    }
  }

  .block-empty,
  .empty-placeholder {
    margin: 12px 0;
  }

  .ranking-card {
    .el-table {
      font-size: 12px;
    }
  }
}
</style>
