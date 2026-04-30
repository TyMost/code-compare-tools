import Vue from 'vue';
import ElementUI from 'element-ui';
import 'element-ui/lib/theme-chalk/index.css';

import App from './App.vue';
import router from './router';
import store from './store';
import './styles/global.scss';

// 导入HTTP插件
import HttpPlugin from './plugins/http';

Vue.config.productionTip = false;
Vue.use(ElementUI);
// 注册HTTP插件
Vue.use(HttpPlugin);

new Vue({
  router,
  store,
  render: (h) => h(App),
}).$mount('#app');
