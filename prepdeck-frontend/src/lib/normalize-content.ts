/**
 * 结构化兜底（对齐 JadeAI 的 src/lib/resume/normalize-content.ts）。
 *
 * 约定：AI 润色 / 生成时，数组字段（highlights / technologies / skills）以
 * JSON 数组形式传输。但模型偶尔会把数组退化成一段「带换行的字符串」，
 * 本模块负责把任意输入规范化为渲染器期望的 string[]，做到幂等、不崩溃。
 *
 * 这是「整数组 JSON 往返」方案的最后一道防线：即使后端没严格返回 JSON 数组，
 * 多行文本也会被拆回多个元素，而不是塌缩进 0 号位。
 */
import { generateId } from './editor-utils';

// PrepDeck 里以 items: [...] 承载列表的区块类型（camelCase）
const ITEM_SECTION_TYPES = new Set<string>([
  'experience',
  'education',
  'project',
  'certification',
  'language',
  'publication',
  'custom',
]);

// 列表条目内部必须是 string[] 的字段（渲染器会 .map / .join）
const ITEM_ARRAY_FIELDS = ['highlights', 'technologies'];

function asObject(v: unknown): Record<string, unknown> | null {
  return v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

/**
 * 把任意值规范化为干净的 string[]。
 * - 已是数组 → 过滤空值后原样返回（幂等）。
 * - 单行字符串 → 单元素数组。
 * - 多行 / 带项目符号（- * •）的字符串 → 按换行拆成多元素并去符号。
 * 注意：不按逗号切分（"React, Node" 应作为单个技能元素保留）。
 */
export function toStringArray(v: unknown): string[] {
  if (Array.isArray(v)) {
    return v
      .filter((x) => x != null && x !== '')
      .map((x) => (typeof x === 'string' ? x : String(x)));
  }
  if (v == null) return [];
  if (typeof v === 'string') {
    const trimmed = v.trim();
    if (!trimmed) return [];
    const parts = trimmed
      .split(/\r?\n/)
      .map((s) => s.replace(/^\s*[-*•]\s?/, '').trim())
      .filter(Boolean);
    return parts.length > 1 ? parts : [trimmed];
  }
  return [String(v)];
}

/**
 * AI 生成 / 旧数据兜底：区块正文是「被 JSON 编码的纯文本字符串」
 * （如 "在公司名称实习期间..." 或 "React, Redux, ..."）。parseSectionContent 会把
 * 这种字符串 JSON.parse 回普通字符串（而非结构化对象），normalizeSectionContent
 * 收到字符串时若直接 asObject 会得到 null，从而把整段文本丢弃——表现为 AI 生成的
 * 简历各区块渲染空白。这里把扁平文本规范化为渲染器期望的结构体，让 AI 内容真正可渲染。
 */
function fromFlatString(type: string, text: string): Record<string, unknown> {
  const trimmed = text.trim();
  if (type === 'skills') {
    // 技能常为逗号分隔的一行文本，拆成技能数组放进单一分类。
    const skills = trimmed
      .split(/[,，、]/)
      .map((s) => s.trim())
      .filter(Boolean);
    return {
      categories: [{ id: generateId(), name: '专业技能', skills }],
    };
  }
  if (type === 'summary') {
    return { text: trimmed };
  }
  if (type === 'personalInfo') {
    // 个人信息是强结构字段，扁平文本无法映射到具体字段（AI 多返回占位符如
    // "姓名/电话/邮箱等（请补充）"），退化为空结构由用户在编辑页补全。
    return {};
  }
  // experience / education / project / certification / language / publication / custom
  // 列表型区块：整段文本作为单条经历的「工作描述」，其余结构化字段留空待补全。
  return {
    items: [
      { id: generateId(), description: trimmed, highlights: [], technologies: [] },
    ],
  };
}

/**
 * 返回某个区块 content 的结构安全副本。永不抛错；对脏数据回退到空但合法的结构。
 */
export function normalizeSectionContent(type: string, rawContent: unknown): Record<string, unknown> {
  // AI 生成 / 旧数据：区块正文可能是被 JSON 编码的纯文本字符串，先做扁平文本兜底。
  if (typeof rawContent === 'string') {
    return fromFlatString(type, rawContent);
  }

  const content = asObject(rawContent);

  if (type === 'skills') {
    const base = content ?? {};
    const cats = Array.isArray(base.categories) ? base.categories : [];
    return {
      ...base,
      categories: cats.map((c) => {
        const cat = asObject(c);
        if (!cat) return { id: generateId(), name: '', skills: [] };
        return { ...cat, id: cat.id || generateId(), skills: toStringArray(cat.skills) };
      }),
    };
  }

  if (ITEM_SECTION_TYPES.has(type)) {
    const base = content ?? {};
    const items = Array.isArray(base.items) ? base.items : [];
    return {
      ...base,
      items: items.map((it) => {
        const item = asObject(it);
        if (!item) return { id: generateId() };
        const next: Record<string, unknown> = { ...item, id: item.id || generateId() };
        for (const field of ITEM_ARRAY_FIELDS) {
          // 只纠正常驻但类型错误的字段；合法缺失的可选字段（如 certification.highlights）不动。
          if (field in next && !Array.isArray(next[field])) {
            next[field] = toStringArray(next[field]);
          }
        }
        return next;
      }),
    };
  }

  // summary / personalInfo / 其他对象型区块
  if (!content) {
    if (type === 'summary') return { text: rawContent == null ? '' : String(rawContent) };
    return {};
  }
  return content;
}
