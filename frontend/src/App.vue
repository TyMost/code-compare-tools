<template>
  <el-container class="app-layout">
    <el-header height="64px" class="app-header">
      <div class="app-header__title">
        ΔO vs ΔG
        <span class="app-header__subtitle">增量迁移对比平台</span>
      </div>
      <el-menu
        :default-active="activeRoute"
        mode="horizontal"
        background-color="transparent"
        @select="handleSelect"
      >
        <el-menu-item index="/dashboard">总览</el-menu-item>
        <el-menu-item index="/diff">文件对比</el-menu-item>
      </el-menu>
    </el-header>
    <el-main class="app-content">
      <router-view />
    </el-main>
  </el-container>
</template>

<script>
export default {
  name: 'App',
  computed: {
    activeRoute() {
      return this.$route.path;
    },
  },
  methods: {
    handleSelect(index) {
      if (index !== this.$route.path) {
        this.$router.push(index);
      }
    },
  },
};
</script>

<style scoped>
.app-header ::v-deep(.el-menu--horizontal) {
  border-bottom: none;
}
</style>
