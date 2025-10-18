import request from './request';

// 迁移工作流 API 封装，对应 core 模块暴露的后端接口
export const fetchOverview = (params) =>
  request.get('/migration/overview', { params });

// 分页查询代码块列表
export const fetchCodeBlocks = (params) =>
  request.get('/migration/code-blocks', { params });

// 获取单个代码块详情
export const fetchCodeBlockDetail = (id) =>
  request.get(`/migration/code-blocks/${id}`);

// 预留 AI 建议接口，对应后端占位实现
export const fetchAISuggestion = (id) =>
  request.get(`/migration/code-blocks/${id}/ai-suggestion`);

// 批量生成迁移注解
export const generateAnnotations = (blockIds, annotationTemplate) =>
  request.post('/migration/code-blocks/generate', {
    blockIds,
    annotationTemplate,
  });

// 批量应用代码块
export const applyBlocks = (blockIds) =>
  request.post('/migration/code-blocks/apply', { blockIds });

// 批量忽略代码块
export const ignoreBlocks = (blockIds) =>
  request.post('/migration/code-blocks/ignore', { blockIds });

// 批量撤回已生成的注解
export const undoBlocks = (blockIds) =>
  request.post('/migration/code-blocks/undo', { blockIds });

// 单条操作入口
export const generateSingle = (id, annotationTemplate) =>
  generateAnnotations([id], annotationTemplate);

export const applySingle = (id) => applyBlocks([id]);

export const ignoreSingle = (id) => ignoreBlocks([id]);

export const undoSingle = (id) => undoBlocks([id]);
