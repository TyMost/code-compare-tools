<template>
  <div class="detail-header">
    <el-button type="text" icon="el-icon-arrow-left" @click="$emit('back')">
      返回
    </el-button>
    <div class="info">
      <div class="file-path">{{ filePath }}</div>
      <div class="meta">
        <span>行号：{{ lineRange }}</span>
        <div class="categories">
          <el-tag
            v-for="item in categories"
            :key="`${filePath}-${item.value}`"
            effect="dark"
            :style="{
              backgroundColor: item.color,
              borderColor: item.color,
            }"
            class="category-tag"
          >
            {{ item.label }}
          </el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DetailHeader',
  props: {
    filePath: {
      type: String,
      default: '',
    },
    lineRange: {
      type: String,
      default: '',
    },
    categories: {
      type: Array,
      default: () => [],
    },
  },
};
</script>

<style scoped lang="scss">
.detail-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 24px;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 56, 88, 0.08);
  margin-bottom: 16px;
}

.info {
  display: flex;
  flex-direction: column;
}

.file-path {
  font-weight: 600;
  color: #1f2933;
}

.meta {
  display: flex;
  gap: 12px;
  font-size: 13px;
  color: #6b7280;
}

.categories {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.category-tag {
  font-weight: 600;
}
</style>
