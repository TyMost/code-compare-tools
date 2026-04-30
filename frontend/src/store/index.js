import Vue from 'vue';
import Vuex from 'vuex';

import diff from './diff';

Vue.use(Vuex);

export default new Vuex.Store({
  modules: {
    diff,
  },
});
