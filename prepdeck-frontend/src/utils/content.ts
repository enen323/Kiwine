/**
 * 内容安全工具 —— 防止后端返回的 {} / 对象 / [object Object] 渲染到预览中
 */
export function safeContent(val: unknown): string {
  if (val == null) return '';
  if (typeof val !== 'string') {
    try {
      const s = JSON.stringify(val);
      return (s === '{}' || s === '[]') ? '' : s;
    } catch { return ''; }
  }
  const t = val.trim();
  if (t === '{}' || t === '[]' || t === '[object Object]') return '';
  return val;
}
