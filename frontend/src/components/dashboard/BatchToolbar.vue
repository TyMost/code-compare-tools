<template>
  <div class="batch-toolbar">
    <div class="actions">
      <el-button
        type="primary"
        :disabled="!canOperate"
        :loading="loading && currentAction === 'generate'"
        @click="handleClick('generate')"
      >
        批量生成
      </el-button>
      <el-button
        type="success"
        :disabled="!canOperate"
        :loading="loading && currentAction === 'apply'"
        @click="handleClick('apply')"
      >
        批量应用
      </el-button>
      <el-button
        type="danger"
        plain
        :disabled="!canOperate"
        :loading="loading && currentAction === 'undo'"
        @click="handleClick('undo')"
      >
        批量撤销
      </el-button>
      <el-button
        type="warning"
        plain
        :disabled="!canOperate"
        :loading="loading && currentAction === 'ignore'"
        @click="handleClick('ignore')"
      >
        批量忽略
      </el-button>
    </div>
    <div class="filters">
      <el-input
        v-model="internalFileName"
        clearable
        placeholder="搜索文件"
        class="file-name-input"
        @keyup.enter.native="emitFileNameChange"
        @clear="emitFileNameChange"
      >
        <el-button
          slot="append"
          icon="el-icon-search"
          @click="emitFileNameChange"
        ></el-button>
      </el-input>
      <el-select
        v-model="internalCategories"
        multiple
        collapse-tags
        placeholder="筛选分类"
        @change="emitFilterChange"
      >
        <el-option
          v-for="option in categoryOptions"
          :key="option.value"
          :label="option.label"
          :value="option.value"
        >
          <span class="option-label">
            <i
              class="color-dot"
              :style="{ backgroundColor: option.color }"
            ></i>
            {{ option.label }}
          </span>
        </el-option>
      </el-select>
    </div>
  </div>
</template>

<script>
export default {
  name: 'BatchToolbar',
  props: {
    selectedIds: {
      type: Array,
      default: () => [],
    },
    categoryOptions: {
      type: Array,
      default: () => [],
    },
    loading: {
      type: Boolean,
      default: false,
    },
    value: {
      type: Array,
      default: () => [],
    },
    fileName: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      internalCategories: [...this.value],
      internalFileName: this.fileName || '',
      currentAction: null,
    };
  },
  computed: {
    canOperate() {
      return this.selectedIds && this.selectedIds.length > 0;
    },
  },
  watch: {
    value(val) {
      this.internalCategories = [...val];
    },
    fileName(val) {
      this.internalFileName = val || '';
    },
  },
  methods: {
    handleClick(action) {
      if (!this.canOperate) {
        return;
      }
      this.currentAction = action;
      this.$emit(action, {
        ids: this.selectedIds,
        done: () => {
          if (this.currentAction === action) {
            this.currentAction = null;
          }
        },
      });
    },
    emitFilterChange(value) {
      this.$emit('input', value);
      this.$emit('filter-change', value);
    },
    emitFileNameChange() {
      const keyword = (this.internalFileName || '').trim();
      this.$emit('file-name-change', keyword);
    },
  },
};
</script>

<style scoped lang="scss">
.batch-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 16px 0;
  padding: 12px 16px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
}

.actions {
  display: flex;
  gap: 12px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 320px;
}

.file-name-input {
  width: 220px;
}

.option-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.color-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
}
</style>

