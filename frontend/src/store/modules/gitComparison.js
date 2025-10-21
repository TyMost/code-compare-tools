import { fetchGitComparison } from '@/api/gitComparison';

const state = () => ({
  sourceProjectKey: '',
  targetProjectKey: '',
  overview: null,
  files: [],
  loading: false,
});

const mutations = {
  SET_SOURCE_KEY(currentState, value) {
    currentState.sourceProjectKey = value || '';
  },
  SET_TARGET_KEY(currentState, value) {
    currentState.targetProjectKey = value || '';
  },
  SET_LOADING(currentState, flag) {
    currentState.loading = !!flag;
  },
  SET_COMPARISON(currentState, payload) {
    if (!payload) {
      currentState.overview = null;
      currentState.files = [];
      return;
    }
    currentState.overview = payload;
    currentState.files = payload.files || [];
  },
};

const actions = {
  setSourceProjectKey({ commit }, value) {
    commit('SET_SOURCE_KEY', value);
  },
  setTargetProjectKey({ commit }, value) {
    commit('SET_TARGET_KEY', value);
  },
  async fetchComparison({ state, commit }, { refresh = false } = {}) {
    if (!state.sourceProjectKey || !state.targetProjectKey) {
      commit('SET_COMPARISON', null);
      return null;
    }
    commit('SET_LOADING', true);
    try {
      const params = {
        sourceProjectKey: state.sourceProjectKey,
        targetProjectKey: state.targetProjectKey,
      };
      if (refresh) {
        params.refresh = true;
      }
      const data = await fetchGitComparison(params);
      commit('SET_COMPARISON', data || null);
      return data;
    } finally {
      commit('SET_LOADING', false);
    }
  },
};

const getters = {
  files: (currentState) => currentState.files || [],
};

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters,
};

