import axios from 'axios';
import { Message } from 'element-ui';

// 鍒涘缓 Axios 瀹炰緥锛屼笌鍚庣 core 妯″潡鎻愪緵鐨?/api/v1 鍓嶇紑淇濇寔涓€鑷?
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  withCredentials: false,
});

// 缁熶竴澶勭悊璇锋眰鍙戝嚭鍓嶇殑鏃ュ織锛屼究浜庢帓鏌ュ弬鏁伴棶棰?
request.interceptors.request.use(
  (config) => {
    const { method, url, params, data } = config;
    console.info('[缃戠粶璇锋眰]', (method || 'GET').toUpperCase(), url, params || data || {});
    return config;
  },
  (error) => {
    console.error('[缃戠粶璇锋眰鏋勫缓澶辫触]', error);
    return Promise.reject(error);
  }
);

// 缁熶竴鐨勫搷搴旂粨鏋勮В鏋愶紝鍏煎 ApiResponse<T> 鍖呰
request.interceptors.response.use(
  (response) => {
    const { data, config } = response;
    if (config && config.responseType === 'blob') {
      console.info('[缃戠粶鍝嶅簲]', config.url, '(blob)');
      return response;
    }
    if (data && Object.prototype.hasOwnProperty.call(data, 'success')) {
      if (data.success) {
        console.info('[缃戠粶鍝嶅簲鎴愬姛]', config.url);
        return typeof data.data !== 'undefined' ? data.data : data;
      }
      const errorMessage = data.message || '璇锋眰澶辫触';
      console.warn('[涓氬姟澶辫触]', errorMessage, data);
      Message.error(errorMessage);
      return Promise.reject(new Error(errorMessage));
    }
    console.info('[鍘熷鍝嶅簲]', config.url);
    return data;
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '缃戠粶寮傚父';
    console.error('[缃戠粶鍝嶅簲寮傚父]', message, error);
    Message.error(message);
    return Promise.reject(error);
  }
);

export default request;

