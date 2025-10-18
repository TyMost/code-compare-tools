<template>
  <div v-if="enabled" class="ai-panel">
    <div class="ai-panel__header">
      <span>AI 迁移生成结果（预览）</span>
      <el-tag size="mini" type="info" v-if="suggestion?.aiModel">
        {{ suggestion.aiModel }}
      </el-tag>
    </div>
    <div class="ai-panel__body" v-loading="loading">
      <template v-if="suggestion && suggestion.enabled">
        <pre class="code-block">{{ suggestion.suggestedCode }}</pre>
        <div class="meta">
          <span>理由：{{ suggestion.reason || '暂无' }}</span>
          <span v-if="suggestion.confidence">
            置信度：{{ Math.round(suggestion.confidence * 100) }}%
          </span>
        </div>
      </template>
      <template v-else>
        <el-empty description="当前无可用的 AI 建议" />
      </template>
    </div>
  </div>
</template>

<script>
export default {
  name: 'AISuggestionPanel',
  props: {
    enabled: {
      type: Boolean,
      default: false,
    },
    suggestion: {
      type: Object,
      default: null,
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
};
</script>

<style scoped lang="scss">
.ai-panel {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  padding: 16px 24px;
  margin-top: 16px;
}

.ai-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 12px;
  color: #1f2933;
}

.code-block {
  background: #0f172a;
  color: #f9fafb;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.meta {
  display: flex;
  gap: 16px;
  margin-top: 12px;
  color: #6b7280;
  font-size: 13px;
}
</style>
