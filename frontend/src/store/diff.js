import {
  scanFull as scanFullRequest,
  fetchDetail as fetchDetailRequest,
} from '../api/diff';
import {
  generateMigration as generateMigrationRequest,
  applyMigration as applyMigrationRequest,
  revertMigration as revertMigrationRequest,
} from '../api/migrate';
import { createTaskId } from '../utils/uid';

const defaultSummary = () => ({
  totalFiles: 0,
  oracleOnly: 0,
  gaussOnly: 0,
  matched: 0,
  consistencyRate: 0,
  overallCoverage: null,
});

const defaultStats = () => ({
  oracleAdded: 0,
  oracleRemoved: 0,
  gaussAdded: 0,
  gaussRemoved: 0,
});

const defaultMatrixFilters = () => ({
  statuses: [],
  coverageRange: [0, 1],
  includeEmptyCoverage: true,
});

const defaultCurrentFile = () => ({
  filePath: '',
  module: '',
  coverage: null,
  oracleDiff: {
    before: '',
    after: '',
  },
  gaussDiff: {
    before: '',
    after: '',
  },
  migrationDiff: '',
  stats: defaultStats(),
});

export default {
  namespaced: true,
  state: () => ({
    taskId: '',
    summary: defaultSummary(),
    overallCoverage: null,
    diffMatrix: [],
    matrixFilters: defaultMatrixFilters(),
    currentFile: defaultCurrentFile(),
    loadingMatrix: false,
    loadingDetail: false,
    migrating: false,
    diffMode: 'deltaO',
  }),
  mutations: {
    setTaskId(state, taskId) {
      state.taskId = taskId || '';
    },
    setSummary(state, payload) {
      state.summary = {
        ...defaultSummary(),
        ...payload,
      };
    },
    setOverallCoverage(state, coverage) {
      state.overallCoverage = coverage === null || coverage === undefined ? null : coverage;
    },
    setDiffMatrix(state, payload) {
      state.diffMatrix = Array.isArray(payload) ? payload : [];
    },
    setMatrixFilters(state, payload = {}) {
      const incoming = Array.isArray(payload.coverageRange)
        ? [...payload.coverageRange]
        : null;
      state.matrixFilters = {
        ...state.matrixFilters,
        statuses: Array.isArray(payload.statuses) ? [...payload.statuses] : state.matrixFilters.statuses,
        coverageRange: incoming || state.matrixFilters.coverageRange,
        includeEmptyCoverage:
          payload.includeEmptyCoverage === undefined
            ? state.matrixFilters.includeEmptyCoverage
            : !!payload.includeEmptyCoverage,
      };
    },
    resetMatrixFilters(state) {
      state.matrixFilters = defaultMatrixFilters();
    },
    setCurrentFile(state, payload) {
      const base = defaultCurrentFile();
      const incomingStats = (payload && payload.stats) || {};
      state.currentFile = {
        ...base,
        ...payload,
        stats: {
          ...base.stats,
          ...incomingStats,
        },
      };
    },
    setLoadingMatrix(state, flag) {
      state.loadingMatrix = flag;
    },
    setLoadingDetail(state, flag) {
      state.loadingDetail = flag;
    },
    setMigrating(state, flag) {
      state.migrating = flag;
    },
    setDiffMode(state, mode) {
      state.diffMode = mode;
    },
  },
  getters: {
    filteredDiffMatrix(state) {
      if (!Array.isArray(state.diffMatrix)) {
        return [];
      }
      const filters = state.matrixFilters || defaultMatrixFilters();
      const statuses = Array.isArray(filters.statuses) ? filters.statuses : [];
      const coverageRange = Array.isArray(filters.coverageRange)
        ? filters.coverageRange
        : [0, 1];
      const includeEmpty =
        filters.includeEmptyCoverage === undefined
          ? true
          : !!filters.includeEmptyCoverage;
      const [min = 0, max = 1] = coverageRange;
      return state.diffMatrix.filter((item) => {
        const statusMatches = !statuses.length || statuses.includes(item.status);
        if (!statusMatches) {
          return false;
        }
        const value = item.coverage;
        if (value === undefined || value === null || value === '') {
          return includeEmpty;
        }
        const numeric = Number(value);
        if (Number.isNaN(numeric)) {
          return includeEmpty;
        }
        const normalizedMin = Number(min);
        const normalizedMax = Number(max);
        const lower = Number.isNaN(normalizedMin) ? 0 : normalizedMin;
        const upper = Number.isNaN(normalizedMax) ? 1 : normalizedMax;
        return numeric >= lower && numeric <= upper;
      });
    },
  },
  actions: {
    async scanFull({ state, commit }, payload = {}) {
      commit('setLoadingMatrix', true);
      try {
        const currentTaskId = state.taskId || createTaskId();
        const response = await scanFullRequest({
          taskId: currentTaskId,
          ...payload,
        });
        commit('setTaskId', response?.taskId || '');
        commit('setSummary', response?.summary || {});
        commit(
          'setOverallCoverage',
          response?.summary?.overallCoverage ?? response?.overallCoverage ?? null,
        );
        commit('setDiffMatrix', response?.diffMatrix || []);
        commit('setCurrentFile', {});
        return response;
      } finally {
        commit('setLoadingMatrix', false);
      }
    },
    async fetchDetail({ state, commit }, { filePath, taskId } = {}) {
      const targetFilePath = filePath || state.currentFile.filePath;
      if (!targetFilePath) {
        throw new Error('缺少文件路径');
      }
      const effectiveTaskId = taskId || state.taskId;
      if (!effectiveTaskId) {
        throw new Error('缺少 taskId，请先执行扫描');
      }
      commit('setLoadingDetail', true);
      try {
        const detail = await fetchDetailRequest({
          taskId: effectiveTaskId,
          filePath: targetFilePath,
        });
        commit('setCurrentFile', detail);
        return detail;
      } finally {
        commit('setLoadingDetail', false);
      }
    },
    async generateMigration({ state, commit, dispatch }, { filePath, options } = {}) {
      const targetFilePath = filePath || state.currentFile.filePath;
      if (!targetFilePath) {
        throw new Error('请选择需要迁移的文件');
      }
      if (!state.taskId) {
        throw new Error('缺少 taskId，请先执行扫描');
      }
      commit('setMigrating', true);
      try {
        const result = await generateMigrationRequest({
          taskId: state.taskId,
          filePath: targetFilePath,
          options: options || {},
        });
        const nextTaskId = result?.data?.taskId;
        if (nextTaskId && nextTaskId !== state.taskId) {
          commit('setTaskId', nextTaskId);
        }
        await dispatch('fetchDetail', {
          filePath: targetFilePath,
          taskId: nextTaskId || state.taskId,
        });
        return result;
      } finally {
        commit('setMigrating', false);
      }
    },
    async applyMigration({ state, commit, dispatch }, { filePath } = {}) {
      const targetFilePath = filePath || state.currentFile.filePath;
      if (!targetFilePath) {
        throw new Error('请选择需要应用迁移的文件');
      }
      if (!state.taskId) {
        throw new Error('缺少 taskId，请先执行扫描');
      }
      commit('setMigrating', true);
      try {
        const result = await applyMigrationRequest({
          taskId: state.taskId,
          filePath: targetFilePath,
        });
        const nextTaskId = result?.data?.taskId;
        if (nextTaskId && nextTaskId !== state.taskId) {
          commit('setTaskId', nextTaskId);
        }
        await dispatch('fetchDetail', {
          filePath: targetFilePath,
          taskId: nextTaskId || state.taskId,
        });
        return result;
      } finally {
        commit('setMigrating', false);
      }
    },
    async revertMigration({ state, commit, dispatch }, { filePath } = {}) {
      const targetFilePath = filePath || state.currentFile.filePath;
      if (!targetFilePath) {
        throw new Error('请选择需要撤销迁移的文件');
      }
      if (!state.taskId) {
        throw new Error('缺少 taskId，请先执行扫描');
      }
      commit('setMigrating', true);
      try {
        const result = await revertMigrationRequest({
          taskId: state.taskId,
          filePath: targetFilePath,
        });
        const nextTaskId = result?.data?.taskId;
        if (nextTaskId && nextTaskId !== state.taskId) {
          commit('setTaskId', nextTaskId);
        }
        await dispatch('fetchDetail', {
          filePath: targetFilePath,
          taskId: nextTaskId || state.taskId,
        });
        return result;
      } finally {
        commit('setMigrating', false);
      }
    },
  },
};
