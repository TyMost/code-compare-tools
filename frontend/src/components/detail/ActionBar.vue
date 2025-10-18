<template>
  <div class="action-bar">
    <el-button
      type="primary"
      :disabled="disableGenerate"
      :loading="loading && currentAction === 'generate'"
      @click="trigger('generate')"
    >
      生成
    </el-button>
    <el-button
      type="info"
      plain
      :disabled="disableUndo"
      :loading="loading && currentAction === 'undo'"
      @click="trigger('undo')"
    >
      撤销
    </el-button>
    <el-button
      type="success"
      :disabled="disableApply"
      :loading="loading && currentAction === 'apply'"
      @click="trigger('apply')"
    >
      应用
    </el-button>
    <el-button
      type="warning"
      plain
      :disabled="disableIgnore"
      :loading="loading && currentAction === 'ignore'"
      @click="trigger('ignore')"
    >
      忽略
    </el-button>
  </div>
</template>

<script>
export default {
  name: 'ActionBar',
  props: {
    status: {
      type: String,
      default: '',
    },
    loading: {
      type: Boolean,
      default: false,
    },
  },
  data() {
    return {
      currentAction: null,
    };
  },
  computed: {
    disableGenerate() {
      return ['migrated_with_annotation', 'migrated', 'applied', 'ignored'].includes(
        this.status
      );
    },
    disableUndo() {
      return this.status !== 'migrated_with_annotation';
    },
    disableApply() {
      return ['migrated', 'applied', 'ignored'].includes(this.status);
    },
    disableIgnore() {
      return this.status === 'ignored';
    },
  },
  methods: {
    trigger(action) {
      this.currentAction = action;
      this.$emit(action, {
        done: () => {
          if (this.currentAction === action) {
            this.currentAction = null;
          }
        },
      });
    },
  },
};
</script>

<style scoped lang="scss">
.action-bar {
  display: flex;
  gap: 12px;
  padding: 16px 24px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  margin-bottom: 16px;
}
</style>
