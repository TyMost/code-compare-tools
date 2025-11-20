import axios from 'axios';

const apiClient = axios.create({
  baseURL: process.env.VUE_APP_API_BASE,
  timeout: 30000,
});

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
  const response = await apiClient({
    method,
    url,
    data: methodUpper === 'GET' ? undefined : body,
    params: methodUpper === 'GET' ? (body || params) : params,
    responseType,
    headers,
    timeout,
  });

  if (rawResponse || responseType === 'blob') {
    return response;
  }
  return normalizeResponse(response.data);
}
