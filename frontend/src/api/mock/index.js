import request from '../request';

let envValue;
if (typeof process !== 'undefined' && process.env) {
  envValue = process.env.VUE_APP_USE_MOCK;
}
const SHOULD_ENABLE_MOCK =
  typeof envValue === 'string' && envValue.toLowerCase() === 'true';

if (SHOULD_ENABLE_MOCK) {
  setupMockAdapter();
}

const categoryMap = {
  unmigrated: { key: 'unmigrated', label: '未迁移', color: '#F56C6C' },
  syntax_refactor: { key: 'syntax_refactor', label: '语法调整', color: '#E6A23C' },
  compat_refactor: { key: 'compat_refactor', label: '兼容适配', color: '#409EFF' },
  migrated: { key: 'migrated', label: '已迁移', color: '#67C23A' },
  review: { key: 'review', label: '待复核', color: '#916DD5' },
  other: { key: 'other', label: '其他', color: '#909399' },
};

const statusMap = {
  unmigrated: { key: 'unmigrated', label: '未处理', color: '#F56C6C' },
  migrated_with_annotation: {
    key: 'migrated_with_annotation',
    label: '已生成注释',
    color: '#409EFF',
  },
  applied: { key: 'applied', label: '已采纳', color: '#67C23A' },
  ignored: { key: 'ignored', label: '已忽略', color: '#909399' },
};

const projectInfo = {
  oldProjectPath: '/workspace/projectA',
  newProjectPath: '/workspace/projectB',
};

let blocks = [
  {
    id: 'CB-001',
    filePath: '/src/user/LoginController.java',
    startLine: 24,
    endLine: 78,
    codeSnippet: 'public Response login(UserLoginRequest request) { /* ... */ }',
    lineCount: 120,
    newLineCount: 42,
    categories: ['syntax_refactor'],
    status: 'migrated_with_annotation',
    oldCode: `public Response login(UserLoginRequest request) {
    return legacyService.login(request);
}`,
    newCode: `public Response login(UserLoginRequest request) {
    return securityFacade.login(request);
}`,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 8).toISOString(),
    updatedAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    aiSuggestionEnabled: true,
  },
  {
    id: 'CB-002',
    filePath: '/src/user/AuthService.kt',
    startLine: 10,
    endLine: 68,
    codeSnippet: 'fun authenticate(credentials: Credentials): Boolean { /* ... */ }',
    lineCount: 96,
    newLineCount: 12,
    categories: ['unmigrated'],
    status: 'unmigrated',
    oldCode: `fun authenticate(credentials: Credentials): Boolean {
    return legacyAuth.login(credentials)
}`,
    newCode: '',
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 7).toISOString(),
    updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 7).toISOString(),
    aiSuggestionEnabled: false,
  },
  {
    id: 'CB-003',
    filePath: '/src/utils/DateFormatter.js',
    startLine: 5,
    endLine: 52,
    codeSnippet: 'export function formatDate(input) { /* ... */ }',
    lineCount: 78,
    newLineCount: 28,
    categories: ['migrated'],
    status: 'applied',
    oldCode: `export function formatDate(input) {
  return moment(input).format('YYYY-MM-DD');
}`,
    newCode: `export function formatDate(input) {
  return dayjs(input).format('YYYY-MM-DD');
}`,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(),
    updatedAt: new Date(Date.now() - 1000 * 60 * 60 * 2).toISOString(),
    aiSuggestionEnabled: true,
  },
  {
    id: 'CB-004',
    filePath: '/src/report/ExportTask.py',
    startLine: 89,
    endLine: 156,
    codeSnippet: 'def export_report(task: Task) -> None: # TODO refactor streaming',
    lineCount: 132,
    newLineCount: 18,
    categories: ['review'],
    status: 'migrated_with_annotation',
    oldCode: `def export_report(task: Task) -> None:
    legacy_writer.write(task.payload)`,
    newCode: `def export_report(task: Task) -> None:
    new_writer.write(task.payload)`,
    createdAt: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(),
    updatedAt: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
    aiSuggestionEnabled: true,
  },
];

function setupMockAdapter() {
  request.defaults.adapter = async function mockAdapter(config) {
    await delay(150);
    const method = (config.method || 'get').toLowerCase();
    const path = normalizePath(config);
    const params = parseParams(config);
    console.info('[Mock 接口]', method.toUpperCase(), path, params);
    try {
      if (method === 'get' && path === '/migration/overview') {
        return okResponse(config, buildOverview());
      }
      if (method === 'get' && path === '/migration/code-blocks') {
        return okResponse(config, listBlocks(params));
      }
      if (method === 'get' && path.startsWith('/migration/code-blocks/')) {
        const id = path.split('/')[3];
        if (!id) {
          return errorResponse(config, 400, '缺少代码块标识');
        }
        if (path.endsWith('/ai-suggestion')) {
          return okResponse(config, buildAISuggestion(id));
        }
        const detail = getBlockDetail(id);
        return detail
          ? okResponse(config, detail)
          : errorResponse(config, 404, '未找到目标代码块');
      }
      if (method === 'post' && path === '/migration/code-blocks/generate') {
        const body = parseBody(config.data);
        const count = updateBlocks(body.blockIds, () => ({
          status: 'migrated_with_annotation',
        }));
        return okResponse(config, { updated: count }, `已生成 ${count} 条迁移注释`);
      }
      if (method === 'post' && path === '/migration/code-blocks/apply') {
        const body = parseBody(config.data);
        const count = updateBlocks(body.blockIds, () => ({
          status: 'applied',
        }));
        return okResponse(config, { updated: count }, `已应用 ${count} 条代码块`);
      }
      if (method === 'post' && path === '/migration/code-blocks/ignore') {
        const body = parseBody(config.data);
        const count = updateBlocks(body.blockIds, () => ({
          status: 'ignored',
        }));
        return okResponse(config, { updated: count }, `已忽略 ${count} 条代码块`);
      }
      return errorResponse(config, 404, `未匹配到 Mock 接口: ${path}`);
    } catch (error) {
      console.error('[Mock 错误]', error);
      return errorResponse(config, 500, 'Mock 服务异常');
    }
  };
}

function buildOverview() {
  const totalLines = blocks.reduce((sum, item) => sum + (item.lineCount || 0), 0);
  const newLines = blocks.reduce((sum, item) => sum + (item.newLineCount || 0), 0);
  const categoryStatMap = {};
  blocks.forEach((block) => {
    const categoryKeys =
      block.categories && block.categories.length
        ? block.categories
        : ['unmigrated'];
    categoryKeys.forEach((key) => {
      const meta = categoryMap[key] || categoryMap.other;
      if (!categoryStatMap[key]) {
        categoryStatMap[key] = {
          key: meta.key,
          label: meta.label,
          color: meta.color,
          lineCount: 0,
        };
      }
      categoryStatMap[key].lineCount += block.lineCount || 0;
    });
  });
  const codeCategoryStats = Object.values(categoryStatMap).map((item) => ({
    ...item,
    percentage: totalLines
      ? Number((item.lineCount / totalLines).toFixed(4))
      : 0,
  }));
  const codeBlockStatusStats = Object.values(statusMap).map((status) => ({
    key: status.key,
    label: status.label,
    color: status.color,
    count: blocks.filter((block) => block.status === status.key).length,
  }));
  return {
    ...projectInfo,
    totalLines,
    newCodeRatio: totalLines ? Number((newLines / totalLines).toFixed(4)) : 0,
    codeCategoryStats,
    codeBlockStatusStats,
    lastSyncedAt: blocks.reduce((latest, item) => {
      return !latest || item.updatedAt > latest ? item.updatedAt : latest;
    }, null),
  };
}

function listBlocks(params) {
  const categoryFilter = toArray(params.categories);
  const keyword = (params.filePath || '').toLowerCase();
  const page = Math.max(parseInt(params.page, 10) || 1, 1);
  const size = Math.max(parseInt(params.size, 10) || 20, 1);
  let filtered = blocks.slice();
  if (categoryFilter.length) {
    filtered = filtered.filter((block) =>
      (block.categories || []).some((key) => categoryFilter.includes(key))
    );
  }
  if (keyword) {
    filtered = filtered.filter((block) =>
      (block.filePath || '').toLowerCase().includes(keyword)
    );
  }
  const total = filtered.length;
  const totalPages = Math.max(Math.ceil(total / size), 1);
  const start = (page - 1) * size;
  const data = filtered.slice(start, start + size).map(serializeBlockSummary);
  return {
    data,
    page,
    size,
    total,
    totalPages,
  };
}

function getBlockDetail(id) {
  const target = blocks.find((item) => item.id === id);
  if (!target) {
    return null;
  }
  return {
    ...serializeBlockSummary(target),
    oldCode: target.oldCode,
    newCode: target.newCode,
    aiSuggestionEnabled: target.aiSuggestionEnabled,
  };
}

function buildAISuggestion(id) {
  const target = blocks.find((item) => item.id === id);
  if (!target) {
    return {
      enabled: false,
    };
  }
  if (!target.aiSuggestionEnabled) {
    return {
      enabled: false,
    };
  }
  return {
    enabled: true,
    suggestedCode: target.newCode || target.oldCode,
    reason: '基于语义匹配生成的迁移建议示例',
    confidence: 0.88,
    aiModel: 'mock-agent',
  };
}

function serializeBlockSummary(block) {
  const statusMeta = statusMap[block.status] || statusMap.unmigrated;
  return {
    id: block.id,
    filePath: block.filePath,
    startLine: block.startLine,
    endLine: block.endLine,
    codeSnippet: block.codeSnippet,
    categories: block.categories,
    status: statusMeta.key,
    statusLabel: statusMeta.label,
    statusColor: statusMeta.color,
    lineCount: block.lineCount,
    newLineCount: block.newLineCount,
    createdAt: block.createdAt,
    updatedAt: block.updatedAt,
  };
}

function updateBlocks(ids, nextStateBuilder) {
  const targetIds = new Set(toArray(ids));
  if (!targetIds.size) {
    return 0;
  }
  let count = 0;
  const now = new Date().toISOString();
  blocks = blocks.map((block) => {
    if (!targetIds.has(block.id)) {
      return block;
    }
    count += 1;
    const nextState = nextStateBuilder(block) || {};
    return {
      ...block,
      ...nextState,
      updatedAt: now,
    };
  });
  return count;
}

function normalizePath(config) {
  let url = config.url || '';
  const baseURL = config.baseURL || '';
  if (baseURL && url.startsWith(baseURL)) {
    url = url.slice(baseURL.length);
  }
  if (!url.startsWith('/')) {
    url = `/${url}`;
  }
  const queryIndex = url.indexOf('?');
  if (queryIndex >= 0) {
    url = url.slice(0, queryIndex);
  }
  return url;
}

function parseParams(config) {
  const params = { ...(config.params || {}) };
  const url = config.url || '';
  const queryIndex = url.indexOf('?');
  if (queryIndex >= 0) {
    const searchParams = new URLSearchParams(url.slice(queryIndex + 1));
    for (const [key, value] of searchParams.entries()) {
      if (params[key] === undefined) {
        params[key] = value;
      }
    }
  }
  return params;
}

function parseBody(data) {
  if (!data) {
    return {};
  }
  if (typeof data === 'string') {
    try {
      return JSON.parse(data);
    } catch (error) {
      return {};
    }
  }
  if (typeof data === 'object') {
    return data;
  }
  return {};
}

function okResponse(config, payload, message = '操作成功') {
  return Promise.resolve({
    status: 200,
    statusText: 'OK',
    config,
    headers: { 'x-mock': 'true' },
    data: {
      success: true,
      message,
      data: payload,
    },
  });
}

function errorResponse(config, status, message) {
  return Promise.resolve({
    status,
    statusText: 'Error',
    config,
    headers: { 'x-mock': 'true' },
    data: {
      success: false,
      message,
    },
  });
}

function toArray(value) {
  if (!value) {
    return [];
  }
  if (Array.isArray(value)) {
    return value.filter(Boolean);
  }
  return String(value)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export default SHOULD_ENABLE_MOCK;
