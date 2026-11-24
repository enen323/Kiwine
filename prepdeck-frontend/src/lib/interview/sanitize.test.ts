import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  stripRoundCompleteToken,
  sanitizeMessages,
  isInterviewOver,
} from './sanitize.ts';

test('结尾单独一行：标记独占一行被移除并压缩空行', () => {
  const input = '面试到此结束，祝你顺利。\n\n[ROUND_COMPLETE]\n';
  const out = stripRoundCompleteToken(input);
  assert.equal(out, '面试到此结束，祝你顺利。');
});

test('句中出现：标记夹在正文中被移除', () => {
  const input = '好的，我们进入下一题。[ROUND_COMPLETE] 如果你还有问题可以随时提出。';
  const out = stripRoundCompleteToken(input);
  assert.equal(out, '好的，我们进入下一题。 如果你还有问题可以随时提出。');
});

test('多次出现：全部移除', () => {
  const input = 'a[ROUND_COMPLETE]b[ROUND COMPLETED]c[ROUND-COMPLETE]d';
  const out = stripRoundCompleteToken(input);
  assert.equal(out, 'abcd');
});

test('大小写不敏感', () => {
  assert.equal(stripRoundCompleteToken('[round_complete]'), '');
  assert.equal(stripRoundCompleteToken('[Round Completed]'), '');
  assert.equal(stripRoundCompleteToken('x[ROUND-COMPLETE]y'), 'xy');
});

test('无标记时原样返回', () => {
  const input = '这是一段正常的面试官回复，没有特殊标记。';
  assert.equal(stripRoundCompleteToken(input), input);
});

test('null / 空安全', () => {
  assert.equal(stripRoundCompleteToken(''), '');
  // @ts-expect-error 测试 null 入参的防御性
  assert.equal(stripRoundCompleteToken(null), '');
  // @ts-expect-error 测试 undefined 入参的防御性
  assert.equal(stripRoundCompleteToken(undefined), '');
});

test('变体：无分隔符也能移除', () => {
  assert.equal(stripRoundCompleteToken('尾[ROUNDCOMPLETE]巴'), '尾巴');
});

test('sanitizeMessages 批量清洗', () => {
  const msgs = [
    { id: 1, interviewId: 9, role: 'AI' as const, content: 'hi[ROUND_COMPLETE]', questionType: null, sequenceNum: 1, tokenCount: 0, createdAt: '' },
    { id: 2, interviewId: 9, role: 'USER' as const, content: 'thanks', questionType: null, sequenceNum: 2, tokenCount: 0, createdAt: '' },
  ];
  const out = sanitizeMessages(msgs);
  assert.equal(out[0].content, 'hi');
  assert.equal(out[1].content, 'thanks');
  // 不修改原数组
  assert.equal(msgs[0].content, 'hi[ROUND_COMPLETE]');
});

test('isInterviewOver 三来源', () => {
  assert.equal(isInterviewOver(null), false);
  assert.equal(isInterviewOver({ status: 'IN_PROGRESS', currentStage: 'REVERSE_QA' } as any, true), true);
  assert.equal(isInterviewOver({ status: 'COMPLETED', currentStage: 'REVERSE_QA' } as any), true);
  assert.equal(isInterviewOver({ status: 'TERMINATED', currentStage: 'REVERSE_QA' } as any), true);
  assert.equal(isInterviewOver({ status: 'IN_PROGRESS', currentStage: 'COMPLETED' } as any), true);
  assert.equal(isInterviewOver({ status: 'IN_PROGRESS', currentStage: 'REVERSE_QA' } as any), false);
});
