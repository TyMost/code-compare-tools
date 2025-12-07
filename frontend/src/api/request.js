import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.VUE_APP_API_BASE,
  timeout: 60000, // 增加到60秒，解决长时间加载问题
});

// 请求重试机制
const retryConfig = {
  retries: 3,
  retryDelay: 1000,
  retryCondition: (error) => {
    // 网络错误或5xx错误时重试
    return !error.response || (error.response.status >= 500 && error.response.status < 600) || error.code === 'NETWORK_ERROR';
  }
};

// 在开发环境输出详细请求和错误信息，便于排查 CORS 等网络问题
if (process.env.NODE_ENV !== 'production') {
  apiClient.interceptors.request.use((config) => {
    const method = (config.method || 'GET').toUpperCase();
    const url = `${config.baseURL || ''}${config.url}`;
    // eslint-disable-next-line no-console
    console.debug(`[API] ${method} ${url}`, {
      params: config.params,
      data: config.data,
      headers: config.headers,
    });
    return config;
  });

  apiClient.interceptors.response.use(
    (response) => {
      const method = (response.config.method || 'GET').toUpperCase();
      const url = `${response.config.baseURL || ''}${response.config.url}`;
      // eslint-disable-next-line no-console
      console.debug(`[API] ${method} ${url} <- ${response.status}`);
      return response;
    },
    (error) => {
      if (error.response) {
        const method = (error.config?.method || 'GET').toUpperCase();
        const url = `${error.config?.baseURL || ''}${error.config?.url || ''}`;
        // eslint-disable-next-line no-console
        console.error(`[API ERROR] ${method} ${url} <- ${error.response.status}`, {
          data: error.response.data,
          headers: error.response.headers,
        });
      } else {
        // eslint-disable-next-line no-console
        console.error('[API ERROR] 请求未到达后端', error);
      }
      return Promise.reject(error);
    },
  );
}

// 重试拦截器
apiClient.interceptors.response.use(null, async (error) => {
  const config = error.config;
  
  // 如果没有配置信息或已重试次数超过限制，直接拒绝
  if (!config || !config.retryCount || config.retryCount >= retryConfig.retries) {
    return Promise.reject(error);
  }
  
  // 检查是否满足重试条件
  if (!retryConfig.retryCondition(error)) {
    return Promise.reject(error);
  }
  
  // 增加重试计数
  config.retryCount = config.retryCount || 0;
  config.retryCount += 1;
  
  // 等待后重试
  await new Promise(resolve => setTimeout(resolve, retryConfig.retryDelay * config.retryCount));
  
  if (process.env.NODE_ENV !== 'production') {
    // eslint-disable-next-line no-console
    console.warn(`[API RETRY] 第${config.retryCount}次重试 ${config.method?.toUpperCase()} ${config.url}`);
  }
  
  return apiClient(config);
});

function normalizeResponse(payload) {
  if (!payload || typeof payload !== 'object') {
    return {
      status: 'success',
      message: '',
      data: payload,
    };
  }

  const { status, message, data, result } = payload;
  if (status && status !== 'success') {
    if (process.env.NODE_ENV !== 'production') {
      // eslint-disable-next-line no-console
      console.error('[API ERROR] 后端返回非 success 状态', payload);
    }
    const error = new Error(message || '请求失败');
    error.payload = payload;
    throw error;
  }

  if (!status) {
    return {
      status: 'success',
      message: '',
      data: payload,
    };
  }

  return {
    status,
    message: message || '',
    data: data !== undefined ? data : result,
  };
}

export default async function request(method, url, body = null, options = {}) {
  const methodUpper = method.toUpperCase();
  const {
    responseType,
    headers,
    rawResponse = false,
    params,
    timeout,
  } = options || {};
  
  const config = {
    method,
    url,
    data: methodUpper === 'GET' ? undefined : body,
    params: methodUpper === 'GET' ? (body || params) : params,
    responseType,
    headers,
    timeout: timeout || 60000, // 默认60秒超时
    retryCount: 0, // 初始化重试计数
  };

  try {
    const response = await apiClient(config);

    if (rawResponse || responseType === 'blob') {
      return response;
    }
    return normalizeResponse(response.data);
  } catch (error) {
    // 增强错误处理
    if (error.code === 'ECONNABORTED') {
      error.message = '请求超时，请检查网络连接或稍后重试';
    } else if (error.code === 'NETWORK_ERROR') {
      error.message = '网络连接失败，请检查网络状态';
    } else if (error.response?.status === 404) {
      error.message = '请求的资源不存在';
    } else if (error.response?.status === 500) {
      error.message = '服务器内部错误，请稍后重试';
    }
    
    throw error;
  }
}
