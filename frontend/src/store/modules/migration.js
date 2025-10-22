import { Message } from 'element-ui';
import {
  fetchOverview,
  fetchCodeBlocks,
  fetchCodeBlockDetail,
  fetchAISuggestion,
  generateAnnotations,
  applyBlocks,
  ignoreBlocks,
  undoBlocks,
  generateSingle,
  applySingle,
  ignoreSingle,
  undoSingle,
} from '@/api/migration';

const defaultPagination = () => ({
  page: 1,
  size: 20,
  total: 0,
  totalPages: 0,
});

const state = () => ({
  overview: null,
  codeBlocks: [],
  pagination: defaultPagination(),
  filters: {
    categories: [],
    excludeCategories: [],
    filePath: '',
    fileName: '',
    projectKey: '',
  },
  loading: false,
  selectedIds: [],
  detail: null,
  detailLoading: false,
  aiSuggestion: null,
  aiLoading: false,
});

const mutations = {
  SET_OVERVIEW(currentState, payload) {
    currentState.overview = payload;
  },
  SET_CODE_BLOCKS(currentState, payload) {
    currentState.codeBlocks = payload || [];
  },
  SET_LOADING(currentState, flag) {
    currentState.loading = flag;
  },
  SET_PAGINATION(currentState, payload) {
    currentState.pagination = {
      ...currentState.pagination,
      ...payload,
    };
  },
  RESET_PAGINATION(currentState) {
    currentState.pagination = defaultPagination();
  },
  SET_FILTERS(currentState, payload) {
    currentState.filters = {
      ...currentState.filters,
      ...payload,
    };
  },
  SET_SELECTED_IDS(currentState, ids) {
    const source = Array.isArray(ids) ? ids : [];
    const seen = new Set();
    const next = [];
    source.forEach((item) => {
      if (item === undefined || item === null) {
        return;
      }
      if (!seen.has(item)) {
        seen.add(item);
        next.push(item);
      }
    });
    const prev = currentState.selectedIds || [];
    const sameLength = prev.length === next.length;
    const sameOrder =
      sameLength && next.every((item, index) => prev[index] === item);
    if (sameOrder) {
      if (process.env.NODE_ENV !== 'production') {
        console.debug('[migration] skip SET_SELECTED_IDS (no changes)', {
          size: next.length,
        });
      }
      return;
    }
    currentState.selectedIds = next;
    if (process.env.NODE_ENV !== 'production') {
      console.debug('[migration] SET_SELECTED_IDS', {
        from: prev.length,
        to: next.length,
      });
    }
  },
  SET_DETAIL(currentState, payload) {
    currentState.detail = payload;
  },
  SET_DETAIL_LOADING(currentState, flag) {
    currentState.detailLoading = flag;
  },
  SET_AI_SUGGESTION(currentState, payload) {
    currentState.aiSuggestion = payload;
  },
  SET_AI_LOADING(currentState, flag) {
    currentState.aiLoading = flag;
  },
};

const getters = {
  categoryOptions: (currentState) =>
    currentState.overview?.codeCategoryStats?.map((item) => ({
      value: item.key,
      label: item.label,
      color: item.color,
      count: item.lineCount ?? item.count ?? 0,
    })) || [],
};

const actions = {
  async initDashboard({ dispatch }, params = {}) {
    const { resetPage = false, ...rest } = params || {};
    console.info('[migration] init dashboard', {
      ...rest,
      resetPage,
    });
    const overview = await dispatch('fetchOverview', rest);
    const projectKey =
      overview?.projectCode || rest?.projectKey || undefined;
    await dispatch('fetchCodeBlocks', {
      resetPage,
      ...rest,
      projectKey,
    });
  },

  async fetchOverview({ commit, state }, params) {
    console.info('[migration] fetch overview', params || {});
    const data = await fetchOverview(params);
    commit('SET_OVERVIEW', data);
    if (data?.projectCode && data.projectCode !== state.filters.projectKey) {
      commit('SET_FILTERS', { projectKey: data.projectCode });
    }
    return data;
  },

  async fetchCodeBlocks({ commit, state }, payload = {}) {
    const {
      resetPage = false,
      page,
      size,
      categories,
      excludeCategories,
      filePath,
      fileName,
      projectKey,
    } = payload;
    const nextFilters = {
      categories:
        categories !== undefined ? categories : state.filters.categories,
      excludeCategories:
        excludeCategories !== undefined
          ? excludeCategories
          : state.filters.excludeCategories,
      filePath: filePath !== undefined ? filePath : state.filters.filePath,
      fileName: fileName !== undefined ? fileName : state.filters.fileName,
      projectKey:
        projectKey !== undefined ? projectKey : state.filters.projectKey,
    };
    if (
      nextFilters.categories !== state.filters.categories ||
      nextFilters.excludeCategories !== state.filters.excludeCategories ||
      nextFilters.filePath !== state.filters.filePath ||
      nextFilters.fileName !== state.filters.fileName ||
      nextFilters.projectKey !== state.filters.projectKey
    ) {
      commit('SET_FILTERS', nextFilters);
    }
    if (resetPage) {
      commit('RESET_PAGINATION');
    }

    const query = {
      page: page !== undefined ? page : state.pagination.page,
      size: size !== undefined ? size : state.pagination.size,
    };
    if (nextFilters.projectKey) {
      query.projectKey = nextFilters.projectKey;
    }
    if (nextFilters.categories?.length) {
      query.categories = nextFilters.categories;
    }
    if (nextFilters.excludeCategories?.length) {
      query.excludeCategories = nextFilters.excludeCategories;
    }
    if (nextFilters.filePath) {
      query.filePath = nextFilters.filePath;
    }
    if (nextFilters.fileName) {
      query.fileName = nextFilters.fileName;
    }

    console.info('[migration] fetch code blocks', query);
    commit('SET_LOADING', true);
    try {
      const data = await fetchCodeBlocks(query);
      const list = data?.data || [];
      commit('SET_CODE_BLOCKS', list);
      commit('SET_PAGINATION', {
        page: data?.page ?? query.page,
        size: data?.size ?? query.size,
        total: data?.total ?? 0,
        totalPages: data?.totalPages ?? 0,
      });
      const retainedSelection = state.selectedIds.filter((id) =>
        list.some((item) => item.id === id)
      );
      commit('SET_SELECTED_IDS', retainedSelection);
      console.info('[migration] list size', list.length);
      return data;
    } finally {
      commit('SET_LOADING', false);
    }
  },

  clearSelection({ commit }) {
    commit('SET_SELECTED_IDS', []);
  },

  setSelectedIds({ commit }, ids) {
    commit('SET_SELECTED_IDS', ids || []);
  },

  async performBatchAction(
    { dispatch, state, commit },
    { type, ids, annotationTemplate, preserveSelection = false }
  ) {
    if (!ids?.length) {
      console.warn('[migration] no selection for batch action');
      return;
    }
    const actionMap = {
      generate: (targetIds) =>
        generateAnnotations(targetIds, annotationTemplate),
      apply: applyBlocks,
      ignore: ignoreBlocks,
      undo: undoBlocks,
    };
    const action = actionMap[type];
    if (!action) {
      console.warn('[migration] unknown batch action', type);
      return;
    }
    console.info('[migration] batch action', type, ids);
    const response = await action(ids);
    Message.success(response?.message || '操作成功');
    if (!preserveSelection) {
      commit('SET_SELECTED_IDS', []);
    }
    await dispatch('fetchOverview', state.filters);
    await dispatch('fetchCodeBlocks', {
      page: state.pagination.page,
      resetPage: false,
    });
  },

  async fetchDetail({ commit, state }, id) {
    commit('SET_DETAIL_LOADING', true);
    try {
      const filters = state.filters || {};
      const pagination = state.pagination || {};
      const params = {};
      const projectKey =
        typeof filters.projectKey === 'string'
          ? filters.projectKey.trim()
          : filters.projectKey || '';
      if (projectKey) {
        params.projectKey = projectKey;
      }
      if (Array.isArray(filters.categories) && filters.categories.length) {
        params.categories = filters.categories.filter(Boolean);
      }
      if (
        Array.isArray(filters.excludeCategories) &&
        filters.excludeCategories.length
      ) {
        params.excludeCategories = filters.excludeCategories.filter(Boolean);
      }
      const filePath =
        typeof filters.filePath === 'string' ? filters.filePath.trim() : '';
      if (filePath) {
        params.filePath = filePath;
      }
      const fileName =
        typeof filters.fileName === 'string' ? filters.fileName.trim() : '';
      if (fileName) {
        params.fileName = fileName;
      }
      if (pagination.page) {
        params.page = pagination.page;
      }
      if (pagination.size) {
        params.size = pagination.size;
      }
      console.info('[migration] fetch block detail', { id, params });
      const detail = await fetchCodeBlockDetail(id, params);
      commit('SET_DETAIL', detail);
      return detail;
    } finally {
      commit('SET_DETAIL_LOADING', false);
    }
  },

  async performDetailAction(
    { dispatch },
    { id, type, annotationTemplate }
  ) {
    const actionMap = {
      generate: () => generateSingle(id, annotationTemplate),
      apply: () => applySingle(id),
      ignore: () => ignoreSingle(id),
      undo: () => undoSingle(id),
    };
    const action = actionMap[type];
    if (!action) {
      console.warn('[migration] unknown detail action', type);
      return null;
    }
    console.info('[migration] single action', type, id);
    const response = await action();
    Message.success(response?.message || '操作成功');
    await dispatch('fetchDetail', id);
    await dispatch('fetchOverview');
    await dispatch('fetchCodeBlocks');
    return response;
  },

  async fetchAISuggestion({ commit }, id) {
    commit('SET_AI_LOADING', true);
    try {
      console.info('[migration] fetch ai suggestion', id);
      const data = await fetchAISuggestion(id);
      commit('SET_AI_SUGGESTION', data);
      return data;
    } finally {
      commit('SET_AI_LOADING', false);
    }
  },
};

export default {
  namespaced: true,
  state,
  mutations,
  actions,
  getters,
};

