import request from './request';

export function fetchGitComparison(params) {
  return request({
    url: '/migration/git-comparison',
    method: 'get',
    params,
  });
}

export function exportGitComparisonBatch(params) {
  return request({
    url: '/migration/git-comparison/batch-export',
    method: 'get',
    params,
    responseType: 'blob',
  });
}
