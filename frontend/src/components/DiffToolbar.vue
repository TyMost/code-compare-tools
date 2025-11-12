<template>
  <div class="diff-toolbar">
    <div class="diff-toolbar__modes">
      <span class="diff-toolbar__label">Diff 模式</span>
      <el-radio-group
        :value="localMode"
        size="small"
        @input="handleModeChange"
      >
        <el-radio-button label="deltaO">ΔO</el-radio-button>
        <el-radio-button label="deltaG">ΔG</el-radio-button>
        <el-radio-button label="deltaCompare">ΔO vs ΔG</el-radio-button>
        <el-radio-button label="migration">迁移结果</el-radio-button>
      </el-radio-group>
    </div>
    <div class="diff-toolbar__options">
      <el-checkbox
        v-model="options.inlineView"
        @change="emitOptions"
      >
        单列视图
      </el-checkbox>
      <el-checkbox
        v-model="options.ignoreWhitespace"
        @change="emitOptions"
      >
        忽略空白
      </el-checkbox>
      <el-checkbox
        v-model="options.collapseUnchanged"
        @change="emitOptions"
      >
        折叠未变更
      </el-checkbox>
    </div>
    <div class="diff-toolbar__actions">
      <el-button
        type="primary"
        size="small"
        :loading="loading"
        :disabled="disableGenerate || loading"
        @click="$emit('generate')"
      >
        生成迁移
      </el-button>
      <el-button
        type="success"
        size="small"
        :loading="loading"
        :disabled="disableApply || loading"
        @click="$emit('apply')"
      >
        应用结果
      </el-button>
      <el-button
        type="danger"
        size="small"
        :loading="loading"
        :disabled="disableRevert || loading"
        @click="$emit('revert')"
      >
        撤销迁移
      </el-button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DiffToolbar',
  props: {
    value: {
      type: String,
      default: 'deltaO',
    },
    loading: {
      type: Boolean,
      default: false,
    },
    disableGenerate: {
      type: Boolean,
      default: false,
    },
    disableApply: {
      type: Boolean,
      default: false,
    },
    disableRevert: {
      type: Boolean,
      default: false,
    },
    defaultOptions: {
      type: Object,
      default: () => ({
        inlineView: false,
        ignoreWhitespace: false,
        collapseUnchanged: false,
      }),
    },
  },
  data() {
    return {
      localMode: 'deltaO',
      options: {
        inlineView: false,
        ignoreWhitespace: false,
        collapseUnchanged: false,
      },
    };
  },
  watch: {
    value: {
      immediate: true,
      handler(next) {
        this.localMode = next || 'deltaO';
      },
    },
    defaultOptions: {
      immediate: true,
      deep: true,
      handler(next) {
        this.options = {
          ...this.options,
          ...next,
        };
      },
    },
  },
  methods: {
    handleModeChange(mode) {
      this.localMode = mode;
      this.$emit('input', mode);
      this.$emit('mode-change', mode);
    },
    emitOptions() {
      this.$emit('options-change', { ...this.options });
    },
  },
};
</script>

<style scoped>
.diff-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.diff-toolbar__modes,
.diff-toolbar__options,
.diff-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.diff-toolbar__label {
  font-size: 13px;
  color: #606266;
}
</style>
