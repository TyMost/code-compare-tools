<template>
  <div class="git-comparison-view">
    <el-card class="filter-card" shadow="never">
      <div class="filters">
        <el-input
          v-model="localSourceKey"
          size="small"
          placeholder="源项目标识（如 projectA-git）"
          class="filter-input"
          clearable
        />
        <el-input
          v-model="localTargetKey"
          size="small"
          placeholder="目标项目标识（如 projectB-git）"
          class="filter-input"
          clearable
        />
        <el-button
          type="primary"
          size="small"
          :loading="loading"
          icon="el-icon-search"
          @click="handleFetch"
        >
          查询
        </el-button>
        <el-button
          size="small"
          :loading="loading"
          icon="el-icon-refresh"
          @click="handleRefresh"
        >
          刷新数据
        </el-button>
      </div>
    </el-card>

    <div v-if="comparison" class="project-overview">
      <el-card class="project-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>源项目</span>
            <el-tag type="info" size="mini">{{ sourceProject?.projectCode || '-' }}</el-tag>
          </div>
        </template>
        <div class="project-meta">
          <div class="meta-item">
            <span class="label">最后生成：</span>
            <span>{{ formatDate(sourceProject?.generatedAt) }}</span>
          </div>
          <div class="meta-item">
            <span class="label">项目根目录：</span>
            <span>{{ formatRoots(sourceProject?.projectRoots) }}</span>
          </div>
          <div class="meta-item">
            <span class="label">基线提交：</span>
            <span>{{ formatCommits(sourceProject?.baseCommits) }}</span>
          </div>
          <div class="meta-item">
            <span class="label">最新提交：</span>
            <span>{{ formatCommits(sourceProject?.latestCommits) }}</span>
          </div>
        </div>
      </el-card>

      <el-card class="project-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span>目标项目</span>
            <el-tag type="success" size="mini">{{ targetProject?.projectCode || '-' }}</el-tag>
          </div>
        </template>
        <div class="project-meta">
          <div class="meta-item">
            <span class="label">最后生成：</span>
            <span>{{ formatDate(targetProject?.generatedAt) }}</span>
          </div>
          <div class="meta-item">
            <span class="label">项目根目录：</span>
            <span>{{ formatRoots(targetProject?.projectRoots) }}</span>
          </div>
          <div class="meta-item">
            <span class="label">基线提交：</span>
            <span>{{ formatCommits(targetProject?.baseCommits) }}</span>
          </div>
          <div class="meta-item">
            <span class="label">最新提交：</span>
            <span>{{ formatCommits(targetProject?.latestCommits) }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <el-card class="file-list-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>文件对比</span>
          <el-tag size="mini" type="info">{{ files.length }} 个文件</el-tag>
        </div>
      </template>
      <el-skeleton :rows="6" animated v-if="loading && !files.length" />
      <el-table
        v-else
        :data="files"
        height="620"
        stripe
        :row-key="row => row.filePath"
        :expand-row-keys="expandedKeys"
        @expand-change="handleExpandChange"
      >
        <el-table-column type="expand">
          <template slot-scope="scope">
            <div class="expand-panel">
              <div class="file-summary">
                <div class="file-path">{{ scope.row.filePath }}</div>
              </div>

              <div class="diff-columns">
                <div class="diff-column">
                  <h5>源项目</h5>
                  <div v-if="hasGitDiff(scope.row.source)" class="diff-table">
                    <div
                      v-for="row in buildGitRows(scope.row.source.gitDiff)"
                      :key="row.key"
                      class="diff-row"
                      :class="row.type"
                    >
                      <div class="line-no">{{ row.leftNo }}</div>
                      <div class="line-content">{{ row.leftText }}</div>
                      <div class="line-no">{{ row.rightNo }}</div>
                      <div class="line-content">{{ row.rightText }}</div>
                    </div>
                  </div>
                  <div v-else class="diff-fallback">
                    <el-alert
                      title="未检测到 Git hunk，展示块级差异片段"
                      type="info"
                      :closable="false"
                      show-icon
                    />
                    <pre class="fallback-snippet">{{ renderBlockFallback(scope.row.source) }}</pre>
                  </div>
                </div>

                <div class="diff-column">
                  <h5>目标项目</h5>
                  <div v-if="hasGitDiff(scope.row.target)" class="diff-table">
                    <div
                      v-for="row in buildGitRows(scope.row.target.gitDiff)"
                      :key="row.key"
                      class="diff-row"
                      :class="row.type"
                    >
                      <div class="line-no">{{ row.leftNo }}</div>
                      <div class="line-content">{{ row.leftText }}</div>
                      <div class="line-no">{{ row.rightNo }}</div>
                      <div class="line-content">{{ row.rightText }}</div>
                    </div>
                  </div>
                  <div v-else class="diff-fallback">
                    <el-alert
                      title="未检测到 Git hunk，展示块级差异片段"
                      type="info"
                      :closable="false"
                      show-icon
                    />
                    <pre class="fallback-snippet">{{ renderBlockFallback(scope.row.target) }}</pre>
                  </div>
                </div>
              </div>

              <div v-if="hasDualComparison(scope.row)" class="dual-comparison">
                <div class="dual-summary">
                  <div>
                    <span class="label">文件相似度：</span>
                    <span>{{ formatPercent(scope.row.dualComparison.fileSimilarity) }}</span>
                  </div>
                  <div>
                    <span class="label">相同行数：</span>
                    <span>{{ scope.row.dualComparison.sameLineCount }}</span>
                  </div>
                  <div>
                    <span class="label">总变动行数：</span>
                    <span>{{ scope.row.dualComparison.totalChangedLines }}</span>
                  </div>
                </div>
                <el-table
                  v-if="scope.row.dualComparison.blocks && scope.row.dualComparison.blocks.length"
                  :data="scope.row.dualComparison.blocks"
                  size="mini"
                  border
                  class="dual-table"
                >
                  <el-table-column prop="index" label="块序号" width="80" />
                  <el-table-column prop="matchStatus" label="匹配状态" width="120">
                    <template slot-scope="blockScope">
                      {{ formatMatchStatus(blockScope.row.matchStatus) }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="blockType" label="块类型" width="120" />
                  <el-table-column prop="changeType" label="变更类型" width="160" />
                  <el-table-column label="块相似度" width="120">
                    <template slot-scope="blockScope">
                      {{ formatPercent(blockScope.row.similarity) }}
                    </template>
                  </el-table-column>
                  <el-table-column prop="sourceChangedLines" label="源变动行" width="120" />
                  <el-table-column prop="targetChangedLines" label="目标变动行" width="120" />
                  <el-table-column prop="referenceLineCount" label="计权行数" width="120" />
                </el-table>
              </div>

              <div class="block-comparison">
                <div class="block-column">
                  <h5>源项目块详情</h5>
                  <div v-if="scope.row.source?.blocks?.length" class="block-list">
                    <div
                      v-for="(block, index) in scope.row.source.blocks"
                      :key="block.blockId || index"
                      class="block-item"
                    >
                      <div class="block-header">
                        <span>块 {{ index + 1 }}</span>
                        <span>相似度：{{ formatPercent(block.diff?.similarityScore) }}</span>
                      </div>
                      <div class="block-table">
                        <div
                          v-for="row in buildBlockRows(block)"
                          :key="row.key"
                          class="diff-row"
                          :class="row.type"
                        >
                          <div class="line-no">{{ row.leftNo }}</div>
                          <div class="line-content">{{ row.leftText }}</div>
                          <div class="line-no">{{ row.rightNo }}</div>
                          <div class="line-content">{{ row.rightText }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <el-empty v-else description="暂无块级差异" />
                </div>
                <div class="block-column">
                  <h5>目标项目块详情</h5>
                  <div v-if="scope.row.target?.blocks?.length" class="block-list">
                    <div
                      v-for="(block, index) in scope.row.target.blocks"
                      :key="block.blockId || index"
                      class="block-item"
                    >
                      <div class="block-header">
                        <span>块 {{ index + 1 }}</span>
                        <span>相似度：{{ formatPercent(block.diff?.similarityScore) }}</span>
                      </div>
                      <div class="block-table">
                        <div
                          v-for="row in buildBlockRows(block)"
                          :key="row.key"
                          class="diff-row"
                          :class="row.type"
                        >
                          <div class="line-no">{{ row.leftNo }}</div>
                          <div class="line-content">{{ row.leftText }}</div>
                          <div class="line-no">{{ row.rightNo }}</div>
                          <div class="line-content">{{ row.rightText }}</div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <el-empty v-else description="暂无块级差异" />
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="filePath" label="文件" min-width="260" />
        <el-table-column
          label="增量变动相似度"
          width="180"
        >
          <template slot-scope="scope">
            <span>
              {{
                scope.row.dualComparison
                  ? formatPercent(scope.row.dualComparison.fileSimilarity)
                  : '-'
              }}
            </span>
          </template>
        </el-table-column>
        <el-table-column
          label="源块数"
          width="120"
        >
          <template slot-scope="scope">
            <span>{{ scope.row.source?.totalBlocks || 0 }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="目标块数"
          width="120"
        >
          <template slot-scope="scope">
            <span>{{ scope.row.target?.totalBlocks || 0 }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { mapState, mapGetters, mapActions } from 'vuex';
import dayjs from 'dayjs';

export default {
  name: 'GitComparisonView',
  data() {
    return {
      localSourceKey: '',
      localTargetKey: '',
      expandedKeys: [],
    };
  },
  computed: {
    ...mapState('gitComparison', [
      'overview',
      'sourceProjectKey',
      'targetProjectKey',
      'loading',
    ]),
    ...mapGetters('gitComparison', ['files']),
    comparison() {
      return this.overview;
    },
    sourceProject() {
      return this.comparison ? this.comparison.sourceProject : null;
    },
    targetProject() {
      return this.comparison ? this.comparison.targetProject : null;
    },
  },
  watch: {
    sourceProjectKey: {
      immediate: true,
      handler(value) {
        this.localSourceKey = value || '';
      },
    },
    targetProjectKey: {
      immediate: true,
      handler(value) {
        this.localTargetKey = value || '';
      },
    },
    '$route.query'() {
      this.applyRouteDefaults();
    },
  },
  created() {
    this.initializeKeys();
    this.bootstrap();
  },
  methods: {
    ...mapActions('gitComparison', [
      'setSourceProjectKey',
      'setTargetProjectKey',
      'fetchComparison',
    ]),
    initializeKeys() {
      const storedSource = this.readStoredKey('source');
      const storedTarget = this.readStoredKey('target');
      if (storedSource && storedSource !== this.sourceProjectKey) {
        this.setSourceProjectKey(storedSource);
      }
      if (storedTarget && storedTarget !== this.targetProjectKey) {
        this.setTargetProjectKey(storedTarget);
      }
      this.applyRouteDefaults();
    },
    applyRouteDefaults() {
      const { source, target } = this.$route.query || {};
      this.applyKeyFromExternal('source', source);
      this.applyKeyFromExternal('target', target);
    },
    applyKeyFromExternal(type, value) {
      if (!value || typeof value !== 'string') {
        return;
      }
      const trimmed = value.trim();
      if (!trimmed) {
        return;
      }
      if (type === 'source' && trimmed !== this.sourceProjectKey) {
        this.setSourceProjectKey(trimmed);
      } else if (type === 'target' && trimmed !== this.targetProjectKey) {
        this.setTargetProjectKey(trimmed);
      }
    },
    readStoredKey(type) {
      if (typeof window === 'undefined' || !window.localStorage) {
        return '';
      }
      return window.localStorage.getItem(`gitComparison:${type}Key`) || '';
    },
    persistKeys() {
      if (typeof window === 'undefined' || !window.localStorage) {
        return;
      }
      const source = (this.localSourceKey || '').trim();
      const target = (this.localTargetKey || '').trim();
      if (source) {
        window.localStorage.setItem('gitComparison:sourceKey', source);
      } else {
        window.localStorage.removeItem('gitComparison:sourceKey');
      }
      if (target) {
        window.localStorage.setItem('gitComparison:targetKey', target);
      } else {
        window.localStorage.removeItem('gitComparison:targetKey');
      }
    },
    async bootstrap() {
      if (this.sourceProjectKey && this.targetProjectKey) {
        await this.fetchComparison();
        this.persistKeys();
      }
    },
    async handleFetch() {
      this.setSourceProjectKey((this.localSourceKey || '').trim());
      this.setTargetProjectKey((this.localTargetKey || '').trim());
      this.expandedKeys = [];
      await this.fetchComparison();
      this.persistKeys();
    },
    async handleRefresh() {
      this.setSourceProjectKey((this.localSourceKey || '').trim());
      this.setTargetProjectKey((this.localTargetKey || '').trim());
      this.expandedKeys = [];
      await this.fetchComparison({ refresh: true });
      this.persistKeys();
    },
    handleExpandChange(row, expandedRows) {
      this.expandedKeys = expandedRows.map((item) => item.filePath);
    },
    hasDualComparison(row) {
      if (!row || !row.dualComparison) {
        return false;
      }
      const view = row.dualComparison;
      const hasBlocks = Array.isArray(view.blocks) && view.blocks.length > 0;
      return hasBlocks || (view.totalChangedLines && view.totalChangedLines > 0);
    },
    formatPercent(value) {
      if (value === null || value === undefined || Number.isNaN(Number(value))) {
        return '-';
      }
      return `${Number(value).toFixed(1)}%`;
    },
    formatMatchStatus(status) {
      if (!status) {
        return '-';
      }
      const normalized = String(status).toUpperCase();
      if (normalized === 'MATCHED') {
        return '已匹配';
      }
      if (normalized === 'SOURCE_ONLY') {
        return '仅源侧';
      }
      if (normalized === 'TARGET_ONLY') {
        return '仅目标侧';
      }
      return status;
    },
    formatDate(value) {
      if (!value) {
        return '-';
      }
      return dayjs(value).format('YYYY-MM-DD HH:mm');
    },
    formatRoots(map = {}) {
      const entries = Object.entries(map);
      if (!entries.length) {
        return '-';
      }
      return entries.map(([key, val]) => `${key}: ${val}`).join(' | ');
    },
    formatCommits(map = {}) {
      const entries = Object.entries(map);
      if (!entries.length) {
        return '-';
      }
      return entries.map(([key, val]) => `${key}@${val}`).join(' | ');
    },
    hasGitDiff(detail) {
      return (
        detail
        && detail.gitDiff
        && Array.isArray(detail.gitDiff.hunks)
        && detail.gitDiff.hunks.length > 0
      );
    },
    buildGitRows(gitDiff) {
      if (!gitDiff || !Array.isArray(gitDiff.hunks)) {
        return [];
      }
      const rows = [];
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
    renderBlockFallback(detail) {
      if (!detail || !detail.blocks) {
        return '未检测到差异块';
      }
      return detail.blocks
        .map((block, index) => {
          const source = (block.diff?.sourceContent || '').trim();
          const target = (block.diff?.targetContent || '').trim();
          return `# 块 ${index + 1}\n源内容：\n${source || '-'}\n\n目标内容：\n${target || '-'}`;
        })
        .join('\n\n');
    },
    buildBlockRows(block) {
      if (!block || !block.diff) {
        return [];
      }
      const sourceLines = block.diff.sourceLines || [];
      const targetLines = block.diff.targetLines || [];
      const length = Math.max(sourceLines.length, targetLines.length);
      let leftLine = Math.max(1, block.diff.sourceStartLine || 1);
      let rightLine = Math.max(1, block.diff.targetStartLine || 1);
      const rows = [];
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
  },
};
</script>

<style lang="scss" scoped>
.git-comparison-view {
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
    }
  }

  .project-overview {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 16px;
    margin-bottom: 16px;

    .project-card {
      .project-meta {
        display: grid;
        gap: 6px;
        font-size: 12px;

        .meta-item {
          display: flex;
          gap: 4px;
          color: #606266;

          .label {
            color: #303133;
            font-weight: 600;
          }
        }
      }
    }
  }

  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-weight: 600;
  }

  .file-list-card {
    margin-top: 16px;
  }

  .expand-panel {
    padding: 16px 0;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .file-summary {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 13px;
    color: #303133;

    .file-path {
      font-weight: 600;
      word-break: break-all;
    }
  }

  .diff-columns {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 16px;
  }

  .diff-column {
    h5 {
      margin: 0 0 8px;
      font-size: 14px;
      font-weight: 600;
    }
  }

  .diff-table {
    border: 1px solid #ebeef5;
    border-radius: 4px;
    overflow: hidden;
    font-family: 'Fira Code', 'Courier New', monospace;
    font-size: 12px;
  }

  .diff-row {
    display: grid;
    grid-template-columns: 60px 1fr 60px 1fr;
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

    .line-no {
      padding: 4px 6px;
      text-align: right;
      color: #909399;
      background: #f5f7fa;
      border-right: 1px solid #ebeef5;
    }

    .line-content {
      padding: 4px 6px;
      white-space: pre;
      word-break: break-word;
    }
  }

  .diff-fallback {
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

  .dual-comparison {
    margin-top: 16px;
    border: 1px solid #ebeef5;
    border-radius: 4px;
    background: #f9fafc;
    padding: 16px;

    .dual-summary {
      display: flex;
      flex-wrap: wrap;
      gap: 16px;
      font-size: 13px;
      color: #303133;

      .label {
        color: #909399;
        margin-right: 6px;
      }
    }

    .dual-table {
      margin-top: 12px;
    }
  }

  .block-comparison {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 16px;

    .block-column {
      h5 {
        margin: 0 0 8px;
        font-size: 14px;
        font-weight: 600;
      }

      .block-list {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }

      .block-item {
        border: 1px solid #ebeef5;
        border-radius: 4px;
        background: #fff;
      }

      .block-header {
        display: flex;
        justify-content: space-between;
        padding: 10px 12px;
        border-bottom: 1px solid #ebeef5;
        font-size: 12px;
        color: #303133;
      }

      .block-table {
        font-family: 'Fira Code', 'Courier New', monospace;
        font-size: 12px;
      }
    }
  }
}
</style>

