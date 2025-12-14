import request from './request';

function toNumber(value) {
  if (value === undefined || value === null || value === '') {
    return null;
  }
  const numeric = Number(value);
  return Number.isNaN(numeric) ? null : numeric;
}

function mapDiffMatrix(items = []) {
  return items.map((item) => ({
    ...item,
    coverage: toNumber(item.coverage),
  }));
}

export async function scanFull(payload) {
  const { data, message } = await request('POST', '/api/scan/full', payload);
  const summary = {
    ...(data?.summary || {}),
    overallCoverage: toNumber(data?.summary?.overallCoverage ?? data?.overallCoverage),
  };

  return {
    ...data,
    summary,
    diffMatrix: mapDiffMatrix(data?.diffMatrix),
    message,
  };
}

export async function fetchDetail({ taskId, filePath }) {
  console.log('API 调用 fetchDetail:', { taskId, filePath });
  
  try {
    const { data, message } = await request('POST', '/api/scan/detail', { taskId, filePath });
    console.log('API 响应 fetchDetail 成功:', { data, message });
    return {
      ...data,
      coverage: toNumber(data?.coverage),
      message,
    };
  } catch (error) {
    console.error('API 响应 fetchDetail 失败:', error);
    throw error;
  }
}

export function fetchPresets() {
  return request('GET', '/api/scan/presets').then((response) => response.data);
}

export function fetchScanCache() {
  return request('GET', '/api/scan/cache').then((response) => response.data || []);
}

export function clearScanCache() {
  return request('DELETE', '/api/scan/cache');
}

export function fetchRecentTasks() {
  return request('GET', '/api/scan/tasks').then((response) => response.data || []);
}

export function exportMultiReport(payload) {
  return request('POST', '/api/scan/report/aggregate', payload, {
    responseType: 'blob',
    rawResponse: true,
  });
}

// ========== 异步导出相关API ==========

/**
 * 创建异步导出任务
 */
export function createAsyncExportTask(payload) {
  return request('POST', '/api/scan/export/async/create', payload);
}

/**
 * 查询异步导出任务状态
 */
export function getAsyncExportTaskStatus(taskId) {
  return request('GET', `/api/scan/export/async/status/${taskId}`);
}

/**
 * 下载异步导出任务生成的文件
 */
export function downloadAsyncExportFile(taskId) {
  return request('GET', `/api/scan/export/async/download/${taskId}`, null, {
    responseType: 'blob',
    rawResponse: true,
  });
}

/**
 * 取消异步导出任务
 */
export function cancelAsyncExportTask(taskId) {
  return request('DELETE', `/api/scan/export/async/cancel/${taskId}`);
}

/**
 * 获取所有异步导出任务列表
 */
export function getAllAsyncExportTasks() {
  return request('GET', '/api/scan/export/async/tasks');
}

/**
 * 清理过期的异步导出任务
 */
export function cleanupExpiredAsyncExportTasks() {
  return request('DELETE', '/api/scan/export/async/cleanup');
}
