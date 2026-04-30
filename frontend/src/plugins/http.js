import request from '../api/request';

// HTTP插件：为Vue实例添加$http方法
const HttpPlugin = {
  install(Vue) {
    // 添加$http方法到Vue原型
    Vue.prototype.$http = {
      get: (url, params, options = {}) => request('GET', url, params, options),
      post: (url, data, options = {}) => request('POST', url, data, options),
      put: (url, data, options = {}) => request('PUT', url, data, options),
      delete: (url, params, options = {}) => request('DELETE', url, params, options),
      request: (config) => {
        const { method, url, data, params, ...options } = config;
        return request(method || 'GET', url, data || params, options);
      }
    };
  }
};

export default HttpPlugin;
