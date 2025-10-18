import Vue from 'vue';
import Vuex from 'vuex';
import migration from './modules/migration';

Vue.use(Vuex);

export default new Vuex.Store({
  modules: {
    migration,
  },
});
