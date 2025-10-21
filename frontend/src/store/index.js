import Vue from 'vue';
import Vuex from 'vuex';
import migration from './modules/migration';
import gitComparison from './modules/gitComparison';

Vue.use(Vuex);

export default new Vuex.Store({
  modules: {
    gitComparison,
    migration,
  },
});
