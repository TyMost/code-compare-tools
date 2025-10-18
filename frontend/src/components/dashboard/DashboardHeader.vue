<template>
  <div class="dashboard-header">
    <div class="dashboard-header__titles">
      <h1>代码迁移比较工具</h1>
      <p class="subtitle">新旧工程路径比较</p>
    </div>
    <div class="dashboard-header__paths" v-if="projectPaths">
      <div class="path">
        <span class="label">旧路径：</span>
        <span class="value">{{ projectPaths.old }}</span>
      </div>
      <div class="path">
        <span class="label">新路径：</span>
        <span class="value">{{ projectPaths.new }}</span>
      </div>
      <div class="path" v-if="projectPaths.lastSyncedAt">
        <span class="label">最近同步：</span>
        <span class="value">{{ projectPaths.lastSyncedAt }}</span>
      </div>
    </div>
    <div class="dashboard-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script>
export default {
  name: 'DashboardHeader',
  props: {
    projectPaths: {
      type: Object,
      default: null,
    },
  },
};
</script>

<style scoped lang="scss">
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  margin-bottom: 16px;
}

.dashboard-header__titles h1 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1f2933;
}

.subtitle {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 14px;
}

.dashboard-header__paths {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 320px;
}

.dashboard-header__actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: 24px;
}

.dashboard-header__actions :deep(.el-button) {
  min-width: 112px;
  font-weight: 600;
}

.path .label {
  color: #728096;
  font-weight: 600;
}

.path .value {
  color: #1f2933;
  word-break: break-all;
}
</style>
