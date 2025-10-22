<template>
  <div class="card">
    <div class="card-header">
      <span>Git 变动行数对比</span>
      <span v-if="diffEngineLabel" class="engine">模式：{{ diffEngineLabel }}</span>
    </div>
    <div class="stats">
      <div class="stat">
        <div class="label">源库变动行数</div>
        <div class="value">{{ formattedSource }}</div>
      </div>
      <div class="divider" />
      <div class="stat">
        <div class="label">目标库变动行数</div>
        <div class="value">{{ formattedTarget }}</div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GitChangeSummaryCard',
  props: {
    sourceLines: {
      type: Number,
      default: 0,
    },
    targetLines: {
      type: Number,
      default: 0,
    },
    diffEngine: {
      type: String,
      default: '',
    },
  },
  computed: {
    formattedSource() {
      return this.formatCount(this.sourceLines);
    },
    formattedTarget() {
      return this.formatCount(this.targetLines);
    },
    diffEngineLabel() {
      const value = (this.diffEngine || '').trim();
      if (!value) {
        return '';
      }
      return value.toUpperCase();
    },
  },
  methods: {
    formatCount(value) {
      const numeric = Number.isFinite(value) ? value : Number(value) || 0;
      return numeric.toLocaleString();
    },
  },
};
</script>

<style scoped lang="scss">
.card {
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  color: #1f2933;
  margin-bottom: 12px;
}

.engine {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}

.stats {
  flex: 1;
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 16px;
}

.stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.value {
  font-size: 24px;
  font-weight: 600;
  color: #111827;
}

.divider {
  width: 1px;
  background-color: #e5e7eb;
}
</style>
