import type { InterviewMessage, InterviewVO } from '../../types';

/**
 * 移除 AI 面试官回复中可能残留的 `[ROUND_COMPLETE]` 标记及其变体。
 *
 * 背景：后端曾要求模型在结束语末尾单独一行写 `[ROUND_COMPLETE]` 标记，
 * 修复后只在「新生成」时剥离，历史落库消息仍可能含该标记。前端渲染层
 * 必须再做一层清洗，否则回看旧面试会看到脏数据。
 *
 * 支持的变体（大小写不敏感，ROUND 与 COMPLETE 之间允许 0~3 个
 * 空格 / 下划线 / 连字符互换，COMPLETE 末尾 D 可选）：
 *   [ROUND_COMPLETE] / [ROUND COMPLETED] / [ROUND-COMPLETE] /
 *   [ROUNDCOMPLETE] 等。
 */
const ROUND_COMPLETE_RE = /\[ROUND[ _-]{0,3}COMPLETED?\]/gi;

export function stripRoundCompleteToken(text: string): string {
  if (text == null) return '';
  let s = String(text);
  s = s.replace(ROUND_COMPLETE_RE, '');
  // 压缩因移除标记产生的多余空行（连续换行之间可夹空格/制表符）
  s = s.replace(/(?:\r?\n)[ \t]*(?:\r?\n)+/g, '\n\n');
  // 句末多余空白
  return s.replace(/\s+$/, '');
}

/** 批量清洗历史消息内容（用于 GET /messages 返回后）。 */
export function sanitizeMessages(msgs: InterviewMessage[]): InterviewMessage[] {
  if (!Array.isArray(msgs)) return [];
  return msgs.map((m) => ({ ...m, content: stripRoundCompleteToken(m.content) }));
}

/**
 * 统一的面试结束态派生判定（三来源并集）：
 * 1. SSE `ai_response` 的 `interviewCompleted` 标志
 * 2. `GET /{id}` 详情的 `status`（COMPLETED / TERMINATED）
 * 3. `currentStage === 'COMPLETED'`
 */
export function isInterviewOver(
  iv: InterviewVO | null | undefined,
  sseCompleted = false,
): boolean {
  if (sseCompleted) return true;
  if (!iv) return false;
  return (
    iv.status === 'COMPLETED' ||
    iv.status === 'TERMINATED' ||
    iv.currentStage === 'COMPLETED'
  );
}
