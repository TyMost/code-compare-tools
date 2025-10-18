import axios from 'axios';
import { Message } from 'element-ui';

// 创建 Axios 实例，与后端 core 模块提供的 /api/v1 前缀保持一致
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: false,
});

// 统一处理请求发出前的日志，便于排查参数问题
request.interceptors.request.use(
  (config) => {
    const { method, url, params, data } = config;
    console.info('[网络请求]', (method || 'GET').toUpperCase(), url, params || data || {});
    return config;
  },
  (error) => {
    console.error('[网络请求构建失败]', error);
    return Promise.reject(error);
  }
);

// 统一的响应结构解析，兼容 ApiResponse<T> 包装
request.interceptors.response.use(
  (response) => {
    const { data, config } = response;
    if (data && Object.prototype.hasOwnProperty.call(data, 'success')) {
      if (data.success) {
        console.info('[网络响应成功]', config.url);
        return typeof data.data !== 'undefined' ? data.data : data;
      }
      const errorMessage = data.message || '请求失败';
      console.warn('[业务失败]', errorMessage, data);
      Message.error(errorMessage);
      return Promise.reject(new Error(errorMessage));
    }
    console.info('[原始响应]', config.url);
    return data;
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常';
    console.error('[网络响应异常]', message, error);
    Message.error(message);
    return Promise.reject(error);
  }
);

export default request;
