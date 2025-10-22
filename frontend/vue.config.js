const path = require('path');
const MonacoWebpackPlugin = require('monaco-editor-webpack-plugin');

module.exports = {
  devServer: {
    proxy: {
      '/api': {
        target: 'http://localhost:8087',
        changeOrigin: true,
        logLevel: 'warn',
      },
    },
  },
  configureWebpack: {
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    plugins: [
      new MonacoWebpackPlugin({
        languages: ['javascript', 'typescript', 'json'],
      }),
    ],
  },
};
