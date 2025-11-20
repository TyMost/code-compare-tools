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
  const { data, message } = await request('POST', '/api/scan/detail', { taskId, filePath });
  return {
    ...data,
    coverage: toNumber(data?.coverage),
    message,
  };
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
