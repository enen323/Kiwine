// AI 密钥仅保存在客户端本地（localStorage），永远不会通过任何接口上传到服务端。
// 发送生成请求时，密钥以 `x-ai-key` 请求头携带，由后端透传给模型供应商。

const AI_KEY_STORAGE = 'prepdeck_ai_key';

export function getAiKey(): string {
  if (typeof window === 'undefined') return '';
  try {
    return localStorage.getItem(AI_KEY_STORAGE) || '';
  } catch {
    return '';
  }
}

export function setAiKey(key: string): void {
  if (typeof window === 'undefined') return;
  try {
    if (key) localStorage.setItem(AI_KEY_STORAGE, key);
    else localStorage.removeItem(AI_KEY_STORAGE);
  } catch {
    /* ignore */
  }
}

// 返回需要随请求发送的 AI 认证头。没有配置密钥时返回空对象（后端会返回 401）。
export function getAIHeaders(): Record<string, string> {
  const key = getAiKey();
  if (!key) return {};
  return { 'x-ai-key': key };
}
