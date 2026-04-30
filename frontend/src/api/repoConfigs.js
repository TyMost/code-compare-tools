import http from './request';

/**
 * 仓库配置管理API
 */

// 获取所有仓库配置
export const fetchAllConfigs = () => {
  return http.get('/api/repo-configs');
};

// 根据ID获取配置
export const fetchConfigById = (id) => {
  return http.get(`/api/repo-configs/${id}`);
};

// 创建新配置
export const createConfig = (config) => {
  return http.post('/api/repo-configs', config);
};

// 更新配置
export const updateConfig = (id, config) => {
  return http.put(`/api/repo-configs/${id}`, config);
};

// 删除配置
export const deleteConfig = (id) => {
  return http.delete(`/api/repo-configs/${id}`);
};

// 批量导入配置
export const importConfigs = (configs) => {
  return http.post('/api/repo-configs/import', configs);
};

// 导出所有配置
export const exportConfigs = () => {
  return http.get('/api/repo-configs/export');
};

// 检查配置名称是否存在
export const checkNameExists = (name) => {
  return http.get('/api/repo-configs/check-name', { params: { name } });
};

// 转换为前端格式的配置数据
export const transformToFrontendFormat = (backendConfig) => {
  if (!backendConfig) return null;
  
  return {
    id: backendConfig.id,
    name: backendConfig.name,
    description: backendConfig.description || '',
    presetName: backendConfig.id, // 使用ID作为presetName
    version: backendConfig.version || '',
    
    // 转换源仓库配置（对应oracle）
    oracle: {
      repoPath: backendConfig.source?.path || '',
      branchFrom: backendConfig.source?.branchFrom || '',
      branchTo: backendConfig.source?.branchTo || '',
      timeFrom: backendConfig.source?.timeFrom || '',
      timeTo: backendConfig.source?.timeTo || '',
      refHint: backendConfig.source?.refHint || '',
      deltaType: backendConfig.source?.deltaType || 'DELTA_O',
      includeWorkingTree: backendConfig.source?.includeWorkingTree || false,
      fetchIfMissing: backendConfig.source?.fetchIfMissing !== false,
      remoteName: backendConfig.source?.remoteName || 'origin',
      scanStrategy: backendConfig.source?.scanStrategy || 'SNAPSHOT',
      snapshotIncludeRemoteRefs: backendConfig.source?.snapshotIncludeRemoteRefs || false,
      snapshotIncludeTags: backendConfig.source?.snapshotIncludeTags || false,
      snapshotMaxRefs: backendConfig.source?.snapshotMaxRefs || 50,
    },
    
    // 转换目标仓库配置（对应gauss）
    gauss: {
      repoPath: backendConfig.target?.path || '',
      branchFrom: backendConfig.target?.branchFrom || '',
      branchTo: backendConfig.target?.branchTo || '',
      timeFrom: backendConfig.target?.timeFrom || '',
      timeTo: backendConfig.target?.timeTo || '',
      refHint: backendConfig.target?.refHint || '',
      deltaType: backendConfig.target?.deltaType || 'DELTA_G',
      includeWorkingTree: backendConfig.target?.includeWorkingTree || false,
      fetchIfMissing: backendConfig.target?.fetchIfMissing !== false,
      remoteName: backendConfig.target?.remoteName || 'origin',
      scanStrategy: backendConfig.target?.scanStrategy || 'SNAPSHOT',
      snapshotIncludeRemoteRefs: backendConfig.target?.snapshotIncludeRemoteRefs || false,
      snapshotIncludeTags: backendConfig.target?.snapshotIncludeTags || false,
      snapshotMaxRefs: backendConfig.target?.snapshotMaxRefs || 50,
    },
  };
};

// 转换为后端格式的配置数据
export const transformToBackendFormat = (frontendConfig) => {
  if (!frontendConfig) return null;
  
  return {
    id: frontendConfig.id,
    name: frontendConfig.name,
    description: frontendConfig.description || '',
    version: frontendConfig.version || '',
    
    source: {
      code: frontendConfig.oracle?.code || 'o',
      path: frontendConfig.oracle?.repoPath || '',
      branchFrom: frontendConfig.oracle?.branchFrom || '',
      branchTo: frontendConfig.oracle?.branchTo || '',
      timeFrom: frontendConfig.oracle?.timeFrom || '',
      timeTo: frontendConfig.oracle?.timeTo || '',
      refHint: frontendConfig.oracle?.refHint || '',
      deltaType: frontendConfig.oracle?.deltaType || 'DELTA_O',
      includeWorkingTree: frontendConfig.oracle?.includeWorkingTree || false,
      fetchIfMissing: frontendConfig.oracle?.fetchIfMissing !== false,
      remoteName: frontendConfig.oracle?.remoteName || 'origin',
      scanStrategy: frontendConfig.oracle?.scanStrategy || 'SNAPSHOT',
      snapshotIncludeRemoteRefs: frontendConfig.oracle?.snapshotIncludeRemoteRefs || false,
      snapshotIncludeTags: frontendConfig.oracle?.snapshotIncludeTags || false,
      snapshotMaxRefs: frontendConfig.oracle?.snapshotMaxRefs || 50,
    },
    
    target: {
      code: frontendConfig.gauss?.code || 'g',
      path: frontendConfig.gauss?.repoPath || '',
      branchFrom: frontendConfig.gauss?.branchFrom || '',
      branchTo: frontendConfig.gauss?.branchTo || '',
      timeFrom: frontendConfig.gauss?.timeFrom || '',
      timeTo: frontendConfig.gauss?.timeTo || '',
      refHint: frontendConfig.gauss?.refHint || '',
      deltaType: frontendConfig.gauss?.deltaType || 'DELTA_G',
      includeWorkingTree: frontendConfig.gauss?.includeWorkingTree || false,
      fetchIfMissing: frontendConfig.gauss?.fetchIfMissing !== false,
      remoteName: frontendConfig.gauss?.remoteName || 'origin',
      scanStrategy: frontendConfig.gauss?.scanStrategy || 'SNAPSHOT',
      snapshotIncludeRemoteRefs: frontendConfig.gauss?.snapshotIncludeRemoteRefs || false,
      snapshotIncludeTags: frontendConfig.gauss?.snapshotIncludeTags || false,
      snapshotMaxRefs: frontendConfig.gauss?.snapshotMaxRefs || 50,
    },
  };
};
