export function downloadBlob(blob, filename = 'download.dat') {
  if (!blob) {
    throw new Error('缺少需要下载的文件内容');
  }
  const url = window.URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.URL.revokeObjectURL(url);
}

export function parseFilename(contentDisposition) {
  if (!contentDisposition) {
    return '';
  }
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(contentDisposition);
  if (match) {
    const value = match[1] || match[2];
    try {
      return decodeURIComponent(value);
    } catch (error) {
      return value;
    }
  }
  return '';
}
