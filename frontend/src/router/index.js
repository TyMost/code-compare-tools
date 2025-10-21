import Vue from 'vue';
import Router from 'vue-router';
import Dashboard from '@/views/Dashboard.vue';
import GitComparisonView from '@/views/GitComparisonView.vue';
import CodeBlockDetail from '@/views/detail/CodeBlockDetail.vue';

Vue.use(Router);

const router = new Router({
  mode: 'history',
  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: Dashboard,
    },
    {
      path: '/git-comparison',
      name: 'GitComparison',
      component: GitComparisonView,
    },
    {
      path: '/code-block/:id',
      name: 'CodeBlockDetail',
      component: CodeBlockDetail,
      props: true,
    },
  ],
});

export default router;
