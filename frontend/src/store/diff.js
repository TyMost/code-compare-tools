import {
  scanFull as scanFullRequest,
  fetchDetail as fetchDetailRequest,
  fetchRecentTasks as fetchRecentTasksRequest,
  exportMultiReport as exportMultiReportRequest,
  fetchScanCache as fetchScanCacheRequest,
  clearScanCache as clearScanCacheRequest,
} from '../api/diff';
import commitApi from '../api/commit';
import {
  generateMigration as generateMigrationRequest,
  applyMigration as applyMigrationRequest,
  revertMigration as revertMigrationRequest,
} from '../api/migrate';
import { fetchDefaultProfiles as fetchRepoProfilesBundle, validateProfiles as validateRepoProfiles } from '../api/repos';
import { createTaskId } from '../utils/uid';
import { downloadBlob, parseFilename } from '../utils/download';

const PROFILE_STORAGE_KEY = 'migratediff.repoProfiles';
const DEFAULT_BUNDLE_META = () => ({
  version: '',
  generatedAt: '',
});

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
  fileExtensions: [],
  excludeTestFiles: false,
  excludePatterns: [],
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
  commitHistory: null,
});

const defaultCommitHistory = () => ({
  filePath: '',
  oracleCommits: [],
  gaussCommits: [],
  totalCount: 0,
  oracleCount: 0,
  gaussCount: 0,
  hasOracleCommits: false,
  hasGaussCommits: false,
});

function sanitizeCoverageRange(range) {
  if (!Array.isArray(range) || range.length !== 2) {
    return [0, 1];
  }
  const [min, max] = range;
  const lower = Number.isNaN(Number(min)) ? 0 : Number(min);
  const upper = Number.isNaN(Number(max)) ? 1 : Number(max);
  return [Math.max(0, lower), Math.min(1, upper)];
}

function buildExportFilters(source = {}) {
  return {
    statuses: Array.isArray(source.statuses) ? [...source.statuses] : [],
    coverageRange: sanitizeCoverageRange(source.coverageRange),
    includeEmptyCoverage:
      source.includeEmptyCoverage === undefined ? true : !!source.includeEmptyCoverage,
    fileExtensions: Array.isArray(source.fileExtensions) ? [...source.fileExtensions] : [],
    excludeTestFiles: source.excludeTestFiles === undefined ? false : !!source.excludeTestFiles,
    excludePatterns: Array.isArray(source.excludePatterns) ? [...source.excludePatterns] : [],
  };
}

function resolveFilenameFromResponse(response, fallback = 'scan-report.csv') {
  const disposition =
    response?.headers?.['content-disposition'] || response?.headers?.get?.('content-disposition');
  const parsed = parseFilename(disposition);
  return parsed || fallback;
}

function readProfilesFromStorage() {
  if (typeof window === 'undefined') {
    return {
      profiles: [],
      meta: DEFAULT_BUNDLE_META(),
    };
  }
  try {
    const stored = window.localStorage.getItem(PROFILE_STORAGE_KEY);
    if (!stored) {
      return {
        profiles: [],
        meta: DEFAULT_BUNDLE_META(),
      };
    }
    const parsed = JSON.parse(stored);
    if (Array.isArray(parsed)) {
      return {
        profiles: parsed,
        meta: DEFAULT_BUNDLE_META(),
      };
    }
    if (parsed && Array.isArray(parsed.profiles)) {
      return {
        profiles: parsed.profiles,
        meta: buildBundleMeta(parsed),
      };
    }
    return {
      profiles: [],
      meta: DEFAULT_BUNDLE_META(),
    };
  } catch (error) {
    // eslint-disable-next-line no-console
    console.warn('[profiles] failed to parse storage payload', error);
    return {
      profiles: [],
      meta: DEFAULT_BUNDLE_META(),
    };
  }
}

function writeProfilesToStorage(profiles = [], meta = DEFAULT_BUNDLE_META()) {
  if (typeof window === 'undefined') {
    return;
  }
  const payload = {
    version: meta?.version || '',
    generatedAt: meta?.generatedAt || '',
    profiles,
  };
  window.localStorage.setItem(PROFILE_STORAGE_KEY, JSON.stringify(payload));
}

function cloneDiffRequest(request = {}) {
  return {
    ...request,
  };
}

function buildBundleMeta(source = {}) {
  if (!source) {
    return DEFAULT_BUNDLE_META();
  }
  return {
    version: source.version || '',
    generatedAt: source.generatedAt || '',
  };
}

function mergeBundleMeta(current = DEFAULT_BUNDLE_META(), incoming = DEFAULT_BUNDLE_META()) {
  const meta = DEFAULT_BUNDLE_META();
  meta.version = incoming.version || current.version || '';
  meta.generatedAt = incoming.generatedAt || current.generatedAt || '';
  return meta;
}

function normalizeBundleInput(payload) {
  if (!payload) {
    return {
      profiles: [],
      meta: DEFAULT_BUNDLE_META(),
    };
  }
  if (payload.bundle || payload.replaceExisting !== undefined) {
    return normalizeBundleInput(payload.bundle);
  }
  if (Array.isArray(payload)) {
    return {
      profiles: payload,
      meta: DEFAULT_BUNDLE_META(),
    };
  }
  if (Array.isArray(payload.profiles)) {
    return {
      profiles: payload.profiles,
      meta: buildBundleMeta(payload),
    };
  }
  return {
      profiles: [],
      meta: DEFAULT_BUNDLE_META(),
    };
}

function normalizeProfile(record = {}, fallbackVersion = '') {
  const oracle = record.oracle || record.source;
  const gauss = record.gauss || record.target;
  if (!oracle || !gauss) {
    throw new Error('配置缺少 oracle 或 gauss 字段');
  }
  const id = (record.id || record.repoId || record.name || record.code || '').trim()
    || `profile-${createTaskId()}`;
  const name = (record.name || record.repoName || id).trim();
  return {
    id,
    name,
    presetName: record.presetName || '',
    version: record.version || fallbackVersion || '',
    oracle: cloneDiffRequest(oracle),
    gauss: cloneDiffRequest(gauss),
  };
}

function normalizeProfiles(payload, fallbackVersion = '') {
  if (!payload) {
    return [];
  }
  const source = Array.isArray(payload) ? payload : [payload];
  const results = [];
  source.forEach((item) => {
    try {
      results.push(normalizeProfile(item, fallbackVersion));
    } catch (error) {
      // eslint-disable-next-line no-console
      console.warn('[profiles] 忽略无效配置', error?.message);
    }
  });
  return results;
}

function mergeProfiles(existing = [], incoming = []) {
  if (!incoming.length) {
    return existing;
  }
  const map = new Map(existing.map((profile) => [profile.id, profile]));
  incoming.forEach((profile) => {
    map.set(profile.id, profile);
  });
  return Array.from(map.values());
}

function resolveSnapshotKey(snapshot) {
  if (!snapshot) {
    return '';
  }
  return snapshot.repoId || snapshot.taskId || '';
}

// 文件模式匹配工具函数
function convertWildcardToRegex(pattern) {
  if (!pattern || typeof pattern !== 'string') {
    return null;
  }
  
  // 检查是否为正则表达式（用 / 包裹）
  if (pattern.startsWith('/') && pattern.endsWith('/') && pattern.length > 2) {
    try {
      return new RegExp(pattern.slice(1, -1));
    } catch (error) {
      console.warn('[pattern] 无效的正则表达式:', pattern, error);
      return null;
    }
  }
  
  // 转换通配符为正则表达式
  const regexPattern = pattern
    .replace(/\./g, '\\.')  // 转义点号
    .replace(/\*/g, '.*')   // * 转换为 .*
    .replace(/\?/g, '.');   // ? 转换为 .
  
  try {
    return new RegExp(`^${regexPattern}$`);
  } catch (error) {
    console.warn('[pattern] 无效的模式:', pattern, error);
    return null;
  }
}

function matchPattern(filePath, pattern) {
  if (!filePath || !pattern) {
    return false;
  }
  
  const regex = convertWildcardToRegex(pattern);
  if (!regex) {
    return false;
  }
  
  // 提取文件名进行匹配
  const fileName = filePath.split('/').pop() || filePath;
  return regex.test(fileName);
}

function isTestFile(filePath) {
  if (!filePath) {
    return false;
  }
  
  const fileName = filePath.split('/').pop() || filePath;
  const testPatterns = [
    /test/i,
    /spec/i,
    /.*test.*/i,
    /.*Test.*/i,
    /_test\./,
    /_spec\./,
    /\.test\./,
    /\.spec\./
  ];
  
  return testPatterns.some(pattern => pattern.test(fileName));
}

function matchesExcludePatterns(filePath, patterns) {
  if (!Array.isArray(patterns) || patterns.length === 0) {
    return false;
  }
  
  return patterns.some(pattern => matchPattern(filePath, pattern));
}

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
    loadingCommitHistory: false,
    migrating: false,
    diffMode: 'deltaO',
    availableTasks: [],
    loadingTasks: false,
    exportingReport: false,
    repoProfiles: [],
    selectedProfileIds: [],
    cachedSnapshots: [],
    loadingSnapshots: false,
    activeRepoId: '',
    profileBundleMeta: DEFAULT_BUNDLE_META(),
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
        fileExtensions: Array.isArray(payload.fileExtensions) 
          ? [...payload.fileExtensions] 
          : state.matrixFilters.fileExtensions,
        excludeTestFiles: payload.excludeTestFiles === undefined 
          ? state.matrixFilters.excludeTestFiles 
          : !!payload.excludeTestFiles,
        excludePatterns: Array.isArray(payload.excludePatterns) 
          ? [...payload.excludePatterns] 
          : state.matrixFilters.excludePatterns,
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
    setAvailableTasks(state, tasks) {
      state.availableTasks = Array.isArray(tasks) ? [...tasks] : [];
    },
    setLoadingTasks(state, flag) {
      state.loadingTasks = flag;
    },
    setExportingReport(state, flag) {
      state.exportingReport = flag;
    },
    setRepoProfiles(state, profiles) {
      state.repoProfiles = Array.isArray(profiles) ? [...profiles] : [];
    },
    setSelectedProfileIds(state, ids) {
      state.selectedProfileIds = Array.isArray(ids) ? [...ids] : [];
    },
    setCachedSnapshots(state, snapshots) {
      state.cachedSnapshots = Array.isArray(snapshots) ? [...snapshots] : [];
    },
    setLoadingSnapshots(state, flag) {
      state.loadingSnapshots = flag;
    },
    setActiveRepoId(state, repoId) {
      state.activeRepoId = repoId || '';
    },
    setProfileBundleMeta(state, meta) {
      const next = meta || {};
      state.profileBundleMeta = {
        version: next.version || '',
        generatedAt: next.generatedAt || '',
      };
    },
    setCommitHistory(state, commitHistory) {
      state.currentFile.commitHistory = commitHistory || null;
    },
    setLoadingCommitHistory(state, flag) {
      state.loadingCommitHistory = flag;
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
      const fileExtensions = Array.isArray(filters.fileExtensions) ? filters.fileExtensions : [];
      const excludeTestFiles = filters.excludeTestFiles || false;
      const excludePatterns = Array.isArray(filters.excludePatterns) ? filters.excludePatterns : [];
      const [min = 0, max = 1] = coverageRange;
      return state.diffMatrix.filter((item) => {
        const statusMatches = !statuses.length || statuses.includes(item.status);
        if (!statusMatches) {
          return false;
        }
        
        // 排除测试文件
        if (excludeTestFiles && isTestFile(item.filePath)) {
          return false;
        }
        
        // 排除自定义模式匹配的文件
        if (matchesExcludePatterns(item.filePath, excludePatterns)) {
          return false;
        }
        
        // 文件扩展名筛选
        if (fileExtensions.length > 0) {
          const fileExt = item.filePath.split('.').pop();
          const normalizedExt = fileExt ? `.${fileExt.toLowerCase()}` : '';
          if (!fileExtensions.includes(normalizedExt)) {
            return false;
          }
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
    availableFileExtensions(state) {
      const extensions = new Set();
      state.diffMatrix.forEach(item => {
        if (item.filePath && typeof item.filePath === 'string') {
          const parts = item.filePath.split('.');
          if (parts.length > 1) {
            const ext = parts.pop().toLowerCase();
            // 过滤异常长的扩展名和无意义扩展名
            if (ext && ext.length <= 10 && ext.length >= 1) {
              extensions.add(`.${ext}`);
            }
          }
        }
      });
      return Array.from(extensions).sort();
    },
  },
  actions: {
    async fetchRecentTasks({ commit }) {
      commit('setLoadingTasks', true);
      try {
        const tasks = await fetchRecentTasksRequest();
        commit('setAvailableTasks', tasks);
        return tasks;
      } finally {
        commit('setLoadingTasks', false);
      }
    },
    async loadProfiles({ commit, dispatch }, options = {}) {
      const { bootstrapDefaults = true } = options || {};
      const stored = readProfilesFromStorage();
      commit('setRepoProfiles', stored.profiles);
      commit('setProfileBundleMeta', stored.meta);
      if (!stored.profiles.length) {
        commit('setSelectedProfileIds', []);
        if (bootstrapDefaults) {
          try {
            const defaults = await dispatch('syncDefaultProfiles');
            return defaults;
          } catch (error) {
            // eslint-disable-next-line no-console
            console.warn('[profiles] failed to sync defaults', error);
          }
        }
      }
      return stored.profiles;
    },
    async syncDefaultProfiles({ commit }) {
      const bundle = await fetchRepoProfilesBundle();
      const normalized = normalizeProfiles(bundle?.profiles || [], bundle?.version || '');
      if (!normalized.length) {
        return [];
      }
      const meta = buildBundleMeta(bundle);
      commit('setRepoProfiles', normalized);
      commit('setProfileBundleMeta', meta);
      commit('setSelectedProfileIds', normalized.map((profile) => profile.id));
      writeProfilesToStorage(normalized, meta);
      return normalized;
    },
    async importProfiles({ state, commit }, payload) {
      const replaceExisting = !!(payload && payload.replaceExisting);
      const bundleWrapper = payload && payload.bundle ? payload.bundle : payload;
      const bundle = normalizeBundleInput(bundleWrapper);
      if (!bundle.profiles.length) {
        throw new Error('配置文件为空或格式无效');
      }
      await validateRepoProfiles({
        version: bundle.meta.version,
        generatedAt: bundle.meta.generatedAt,
        profiles: bundle.profiles,
      });
      const normalized = normalizeProfiles(bundle.profiles, bundle.meta.version);
      const merged = replaceExisting ? normalized : mergeProfiles(state.repoProfiles, normalized);
      const nextMeta = replaceExisting
        ? bundle.meta
        : mergeBundleMeta(state.profileBundleMeta, bundle.meta);
      commit('setRepoProfiles', merged);
      commit('setProfileBundleMeta', nextMeta);
      writeProfilesToStorage(merged, nextMeta);
      return merged;
    },
    removeProfiles({ state, commit }, ids = []) {
      if (!Array.isArray(ids) || !ids.length) {
        return state.repoProfiles;
      }
      const remain = state.repoProfiles.filter((profile) => !ids.includes(profile.id));
      commit('setRepoProfiles', remain);
      writeProfilesToStorage(remain, state.profileBundleMeta);
      const nextSelection = state.selectedProfileIds.filter((id) => remain.find((item) => item.id === id));
      commit('setSelectedProfileIds', nextSelection);
      return remain;
    },
    exportProfiles({ state }) {
      if (!state.repoProfiles.length) {
        throw new Error('暂无配置可导出');
      }
      const meta = state.profileBundleMeta || DEFAULT_BUNDLE_META();
      return {
        version: meta.version || `local-${Date.now()}`,
        generatedAt: meta.generatedAt || new Date().toISOString(),
        profiles: state.repoProfiles,
      };
    },
    async loadSnapshots({ commit, dispatch }, { autoApply = false, preferredRepoId } = {}) {
      commit('setLoadingSnapshots', true);
      try {
        const snapshots = await fetchScanCacheRequest();
        commit('setCachedSnapshots', snapshots || []);
        if (
          autoApply
          && Array.isArray(snapshots)
          && snapshots.length > 0
        ) {
          const target = snapshots.find((item) => resolveSnapshotKey(item) === preferredRepoId)
            || snapshots[0];
          await dispatch('applySnapshot', target);
        }
        return snapshots;
      } finally {
        commit('setLoadingSnapshots', false);
      }
    },
    async applySnapshot({ commit }, snapshot) {
      if (!snapshot || !snapshot.response) {
        commit('setActiveRepoId', '');
        commit('setTaskId', '');
        commit('setSummary', defaultSummary());
        commit('setOverallCoverage', null);
        commit('setDiffMatrix', []);
        commit('setCurrentFile', {});
        return null;
      }
      const key = resolveSnapshotKey(snapshot);
      commit('setActiveRepoId', key);
      const response = snapshot.response;
      commit('setTaskId', response?.taskId || snapshot.taskId || '');
      commit(
        'setOverallCoverage',
        response?.summary?.overallCoverage ?? response?.overallCoverage ?? null,
      );
      commit('setSummary', response?.summary || {});
      commit('setDiffMatrix', response?.diffMatrix || []);
      commit('setCurrentFile', {});
      return snapshot;
    },
    async clearSnapshots({ commit }) {
      await clearScanCacheRequest();
      commit('setCachedSnapshots', []);
      commit('setActiveRepoId', '');
      commit('setTaskId', '');
      commit('setSummary', defaultSummary());
      commit('setOverallCoverage', null);
      commit('setDiffMatrix', []);
      commit('setCurrentFile', {});
    },
    async scanFull({ state, commit }, payload = {}) {
      commit('setLoadingMatrix', true);
      try {
        const currentTaskId = payload.taskId || state.taskId || createTaskId();
        const response = await scanFullRequest({
          ...payload,
          taskId: currentTaskId,
        });
        commit('setTaskId', response?.taskId || currentTaskId || '');
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
    async scanProfiles({ state, dispatch }, { profileIds }) {
      const targets = Array.isArray(profileIds) && profileIds.length
        ? state.repoProfiles.filter((profile) => profileIds.includes(profile.id))
        : [];
      if (!targets.length) {
        throw new Error('请选择需要刷新的仓库配置');
      }
      for (const profile of targets) {
        await dispatch('scanFull', {
          taskId: createTaskId(),
          repoId: profile.id,
          repoName: profile.name,
          presetName: profile.presetName,
          persistResult: true,
          oracle: profile.oracle,
          gauss: profile.gauss,
        });
      }
      await dispatch('loadSnapshots', {
        autoApply: true,
        preferredRepoId: targets[targets.length - 1]?.id,
      });
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
    async exportMultiReport({ commit }, { repos, filters, format = 'csv' } = {}) {
      if (!Array.isArray(repos) || repos.length === 0) {
        throw new Error('请至少选择一个任务');
      }
      
      // 验证每个仓库选择的配置
      const invalidRepos = repos.filter(repo => !repo.taskId && !repo.presetName);
      if (invalidRepos.length > 0) {
        console.error('无效的仓库配置:', invalidRepos);
        throw new Error(`存在无效的仓库配置，缺少 taskId 和 presetName`);
      }
      
      console.log('导出多仓库报告:', { repos, filters, format });
      
      const exportFilters = buildExportFilters(filters || {});
      commit('setExportingReport', true);
      try {
        const requestPayload = {
          repos,
          statuses: exportFilters.statuses,
          coverageMin: exportFilters.coverageRange[0],
          coverageMax: exportFilters.coverageRange[1],
          includeEmptyCoverage: exportFilters.includeEmptyCoverage,
          fileExtensions: exportFilters.fileExtensions,
          excludeTestFiles: exportFilters.excludeTestFiles,
          excludePatterns: exportFilters.excludePatterns,
          format,
        };
        
        console.log('发送导出请求:', requestPayload);
        const response = await exportMultiReportRequest(requestPayload);
        
        const filename = resolveFilenameFromResponse(
          response,
          `scan-report-multi-${Date.now()}.${format}`,
        );
        downloadBlob(response.data, filename);
        console.log('导出成功:', filename);
        return filename;
      } catch (error) {
        console.error('导出失败:', error);
        
        // 提供更友好的错误信息
        let errorMessage = '导出失败';
        if (error.response) {
          if (error.response.status === 400) {
            const errorText = await error.response.text();
            console.error('400错误详情:', errorText);
            
            if (errorText.includes('Unknown scan preset')) {
              errorMessage = '找不到预设配置，请确保配置已正确导入或使用默认配置';
            } else if (errorText.includes('找不到 taskId')) {
              errorMessage = '找不到对应的扫描任务，请先执行扫描后再导出';
            } else if (errorText.includes('暂无扫描记录')) {
              errorMessage = '暂无扫描记录，请先执行扫描';
            } else {
              errorMessage = `请求参数错误: ${errorText}`;
            }
          } else if (error.response.status === 500) {
            errorMessage = '服务器内部错误，请稍后重试';
          } else {
            errorMessage = `导出失败 (${error.response.status}): ${error.message || '未知错误'}`;
          }
        } else if (error.message) {
          errorMessage = error.message;
        }
        
        throw new Error(errorMessage);
      } finally {
        commit('setExportingReport', false);
      }
    },
    async fetchCommitHistory({ state, commit }, { filePath, taskId } = {}) {
      const targetFilePath = filePath || state.currentFile.filePath;
      if (!targetFilePath) {
        throw new Error('缺少文件路径');
      }
      const effectiveTaskId = taskId || state.taskId;
      if (!effectiveTaskId) {
        throw new Error('缺少 taskId，请先执行扫描');
      }
      commit('setLoadingCommitHistory', true);
      try {
        const response = await commitApi.getFileCommitHistory({
          taskId: effectiveTaskId,
          filePath: targetFilePath,
        });
        const commitHistory = commitApi.parseCommitHistory(response.data);
        commit('setCommitHistory', commitHistory);
        return commitHistory;
      } catch (error) {
        console.warn('获取提交历史失败:', error);
        // 失败时设置空的提交历史
        const emptyCommitHistory = defaultCommitHistory();
        emptyCommitHistory.filePath = targetFilePath;
        commit('setCommitHistory', emptyCommitHistory);
        return emptyCommitHistory;
      } finally {
        commit('setLoadingCommitHistory', false);
      }
    },
  },
};
