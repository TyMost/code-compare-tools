import request from './request';

export function fetchDefaultProfiles() {
  return request('GET', '/api/repo-profiles/defaults').then((response) => response.data || {});
}

export function validateProfiles(bundle) {
  return request('POST', '/api/repo-profiles/validate', bundle).then((response) => response.data || bundle);
}
