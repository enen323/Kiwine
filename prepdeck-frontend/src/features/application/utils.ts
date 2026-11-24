// 投递记录模块的纯函数工具：日期格式化 / 解析 / 错误归一化
import type { ApplicationStatus } from './types';

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n);
}

/**
 * 将 ISO 字符串格式化为「YYYY-MM-DD HH:mm」（本地时区）。
 * 空值返回空字符串，供表格 / 详情渲染。
 */
export function formatDateTime(iso?: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ` +
    `${pad(d.getHours())}:${pad(d.getMinutes())}`
  );
}

/**
 * Date -> 本地时区 ISO 字符串 `YYYY-MM-DDTHH:mm:ss`（不含 Z）。
 * 与后端 appliedAt 契约（如 `2025-01-15T10:30:00`，本地无时区后缀）一致，
 * 避免 toISOString() 的 UTC 偏移导致时间错位。
 */
export function toISO(date: Date): string {
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

/**
 * Date -> 原生 <input type="datetime-local"> 需要的本地值「YYYY-MM-DDTHH:mm」。
 */
export function dateToLocalInput(date?: Date | null): string {
  if (!date || Number.isNaN(date.getTime())) return '';
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/** 原生 datetime-local 值「YYYY-MM-DDTHH:mm」 -> 本地 Date；空值返回 undefined。 */
export function localInputToDate(value: string): Date | undefined {
  if (!value) return undefined;
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? undefined : d;
}

/**
 * 错误归一化：优先返回后端 message；否则按 job-service-api.md §四 业务码映射中文。
 * 投递记录相关：20000 未登录 / 30000 记录不存在或越权 / 40000 简历不存在 / 10002 参数校验。
 * AI 求职信相关：70000 免费额度用完 / 50003 AI 服务异常。
 */
export function normalizeApplicationError(code: number, message?: string): string {
  if (message) return message;
  switch (code) {
    case 20000:
      return '未登录，请重新登录后再试';
    case 30000:
      return '投递记录不存在或无权访问';
    case 40000:
      return '关联的简历不存在';
    case 10002:
      return '参数校验不通过，请检查输入';
    case 70000:
      return '本月免费 AI 额度已用完';
    case 50003:
      return 'AI 服务异常，请稍后重试';
    default:
      return '操作失败，请稍后重试';
  }
}

/** 把任意字符串状态安全映射到已知状态集合（防御后端返回未知值）。 */
export function asApplicationStatus(value: string): ApplicationStatus {
  const known: ApplicationStatus[] = [
    'applied',
    'interviewing',
    'offered',
    'rejected',
    'withdrawn',
  ];
  return (known.includes(value as ApplicationStatus)
    ? value
    : 'applied') as ApplicationStatus;
}
