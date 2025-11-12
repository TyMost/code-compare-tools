import request from './request';

export function generateMigration(payload) {
  return request('POST', '/api/migrate/generate', payload);
}

export function applyMigration(payload) {
  return request('POST', '/api/migrate/apply', payload);
}

export function revertMigration(payload) {
  return request('POST', '/api/migrate/revert', payload);
}
