<template>
  <div class="incremental-diff">
    <div v-if="normalizedSegments.length" class="legend">
      <span class="legend-item remove">源删除</span>
      <span class="legend-item add">目标新增</span>
      <span class="legend-item change">修改</span>
    </div>
    <div
      v-for="(segment, index) in normalizedSegments"
      :key="index"
      class="diff-segment"
      :class="segment.className"
    >
      <div class="segment-header">
        <div class="header-left">
          <span class="segment-type">{{ segment.displayType }}</span>
          <span class="segment-meta">
            源 L{{ segment.sourceStart }} · 目标 L{{ segment.targetStart }} · 行数 {{ segment.changedLines }}
          </span>
        </div>
        <div class="header-right">
          <span>源</span>
          <span>目标</span>
        </div>
      </div>
      <div class="segment-body">
        <div class="segment-pane removed">
          <pre><code>{{ segment.sourceText }}</code></pre>
        </div>
        <div class="segment-pane added">
          <pre><code>{{ segment.targetText }}</code></pre>
        </div>
      </div>
    </div>
    <div v-if="!normalizedSegments.length" class="empty-state">
      无增量差异段
    </div>
  </div>
</template>

<script>
export default {
  name: 'IncrementalDiffViewer',
  props: {
    segments: {
      type: Array,
      default: () => [],
    },
  },
  computed: {
    normalizedSegments() {
      return (this.segments || []).map((segment) => {
        const type = (segment?.type || 'CHANGE').toString().toUpperCase();
        const displayMap = {
          INSERT: '新增',
          ADD: '新增',
          DELETE: '删除',
          REMOVE: '删除',
          CHANGE: '修改',
        };
        const displayType = displayMap[type] || type;
        const className =
          type === 'INSERT' || type === 'ADD'
            ? 'segment-add'
            : type === 'DELETE' || type === 'REMOVE'
              ? 'segment-delete'
              : 'segment-change';
        const sourceLines =
          segment?.sourceContent ||
          (Array.isArray(segment?.sourceLines)
            ? segment.sourceLines.join('\n')
            : '');
        const targetLines =
          segment?.targetContent ||
          (Array.isArray(segment?.targetLines)
            ? segment.targetLines.join('\n')
            : '');
        return {
          displayType,
          className,
          sourceStart: segment?.sourceStartLine || 0,
          targetStart: segment?.targetStartLine || 0,
          changedLines: segment?.changedLineCount || 0,
          sourceText: sourceLines || '',
          targetText: targetLines || '',
        };
      });
    },
  },
};
</script>

<style scoped lang="scss">
.incremental-diff {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.legend {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #6b7280;
}

.legend-item {
  padding: 2px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
}

.legend-item.remove {
  background: #fef2f2;
  border-color: #fecaca;
}

.legend-item.add {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.legend-item.change {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.diff-segment {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  overflow: hidden;
  background: #ffffff;
}

.segment-header {
  display: grid;
  grid-template-columns: 1fr 180px;
  align-items: center;
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 500;
  background-color: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.segment-header .header-left {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  color: #1f2937;
}

.segment-header .segment-meta {
  font-size: 12px;
  color: #6b7280;
}

.segment-header .header-right {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  text-align: center;
  font-size: 12px;
  color: #6b7280;
}

.segment-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 12px;
}

.segment-pane {
  border-radius: 8px;
  padding: 12px;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 120px;
  background-color: #f9fafb;
  border: 1px solid #e5e7eb;

  pre {
    margin: 0;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
  }
}

.segment-pane.added {
  background-color: #ecfdf5;
  border-color: #a7f3d0;
}

.segment-pane.removed {
  background-color: #fef2f2;
  border-color: #fecaca;
}

.segment-add .segment-header {
  background-color: #ecfdf5;
  border-bottom-color: #a7f3d0;
}

.segment-delete .segment-header {
  background-color: #fef2f2;
  border-bottom-color: #fecaca;
}

.segment-change .segment-header {
  background-color: #eff6ff;
  border-bottom-color: #bfdbfe;
}

.empty-state {
  padding: 40px 0;
  text-align: center;
  color: #9ca3af;
  border: 1px dashed #d1d5db;
  border-radius: 12px;
}
</style>
