import Vue from 'vue';
import VueRouter from 'vue-router';

Vue.use(VueRouter);

const DashboardPage = () => import('../pages/DashboardPage.vue');
const FileDiffPage = () => import('../pages/FileDiffPage.vue');

const router = new VueRouter({
  mode: 'hash',
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: DashboardPage,
      meta: {
        title: '差异概览',
      },
    },
    {
      path: '/diff',
      name: 'FileDiff',
      component: FileDiffPage,
      props: (route) => ({
        taskId: route.query.taskId || '',
        filePath: route.query.filePath || '',
        oracleDelta: route.query.oracleDelta || '',
        gaussDelta: route.query.gaussDelta || '',
      }),
      meta: {
        title: '文件对比',
      },
    },
  ],
});

router.beforeEach((to, from, next) => {
  console.log('[Router] 导航从', from.path, '到', to.path);
  next();
});

router.afterEach((to) => {
  console.log('[Router] 成功导航到', to.path);
  if (to.meta && to.meta.title) {
    document.title = `${to.meta.title} - ΔO vs ΔG 对比平台`;
  }
});

export default router;
