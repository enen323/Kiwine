import type { EditorSection, SectionContent } from '../types/editor';
import type { ResumeSectionType, PolishResponse } from '../types';
import { resumeApi } from '../api/resume';
import { normalizeSectionContent, toStringArray } from './normalize-content';

function mapSectionType(type: string): ResumeSectionType {
  switch (type) {
    case 'personalInfo':
      return 'personalInfo';
    case 'education':
      return 'education';
    case 'experience':
      return 'experience';
    case 'project':
      return 'project';
    case 'skills':
      return 'skills';
    case 'summary':
      return 'summary';
    case 'certification':
      return 'certification';
    case 'language':
      return 'language';
    case 'publication':
      return 'publication';
    default:
      return 'custom';
  }
}

/**
 * 路径用「段数组」而非字符串，支持任意深度数组索引，
 * 例如 highlights 第 i 条经历的第 j 个要点 → ['items', i, 'highlights']。
 */
type PathSegment = string | number;
interface PolishField {
  path: PathSegment[];
  text: string;
  /** 'text' = 纯文本字段；'array' = 数组字段（以 JSON 数组形式往返） */
  kind: 'text' | 'array';
}

function setByPath(obj: unknown, segments: PathSegment[], value: unknown): void {
  let cur = obj as Record<string, unknown>;
  for (let i = 0; i < segments.length - 1; i++) {
    const key = String(segments[i]);
    if (cur[key] == null) {
      const nextSeg = segments[i + 1];
      cur[key] = typeof nextSeg === 'number' ? [] : {};
    }
    cur = cur[key] as Record<string, unknown>;
  }
  cur[String(segments[segments.length - 1])] = value;
}

/**
 * 把后端返回的数组文本解析回 string[]。
 * 优先按 JSON 解析（整数组 JSON 方案）；若后端退化成多行纯文本，
 * 用 toStringArray 兜底拆回多元素，避免塌缩到 0 号位。
 */
function parsePolishedArray(polished: string): string[] {
  const trimmed = polished.trim();
  if (!trimmed) return [];
  try {
    const parsed = JSON.parse(trimmed);
    if (Array.isArray(parsed)) {
      return parsed
        .filter((x) => x != null && x !== '')
        .map((x) => (typeof x === 'string' ? x : String(x)));
    }
  } catch {
    // 不是合法 JSON → 走文本兜底
  }
  return toStringArray(trimmed);
}

/**
 * 提取可润色字段（对齐 JadeAI 的整数组 JSON 方案）。
 *
 * - 字符串字段（summary.text / 各 item.description）：整体作为一段文本发送。
 * - 数组字段（highlights / technologies / skills）：以 JSON.stringify(string[])
 *   整体发送，后端应整体返回 JSON 数组，前端再整体写回。数组元素数量 1:1 保留。
 */
export function extractPolishableFields(section: EditorSection): PolishField[] {
  const fields: PolishField[] = [];
  const c = section.content as unknown as Record<string, unknown>;

  const pushItemArray = (items: Record<string, unknown>[], arrayKeys: string[]) => {
    items.forEach((it, i) => {
      if (typeof it.description === 'string' && it.description.trim()) {
        fields.push({ path: ['items', i, 'description'], text: it.description, kind: 'text' });
      }
      for (const key of arrayKeys) {
        if (Array.isArray(it[key])) {
          const arr = it[key] as string[];
          if (arr.some((v) => v && v.trim())) {
            fields.push({
              path: ['items', i, key],
              text: JSON.stringify(arr), // ★ 整数组 JSON 发送
              kind: 'array',
            });
          }
        }
      }
    });
  };

  switch (section.type) {
    case 'summary':
      if (typeof c.text === 'string' && c.text.trim()) {
        fields.push({ path: ['text'], text: c.text, kind: 'text' });
      }
      break;
    case 'experience':
    case 'project':
      pushItemArray(Array.isArray(c.items) ? (c.items as Record<string, unknown>[]) : [], [
        'highlights',
        'technologies',
      ]);
      break;
    case 'education':
    case 'custom':
    case 'language':
    case 'publication':
      pushItemArray(Array.isArray(c.items) ? (c.items as Record<string, unknown>[]) : [], ['highlights']);
      break;
    case 'skills': {
      const categories = Array.isArray(c.categories) ? (c.categories as Record<string, unknown>[]) : [];
      categories.forEach((cat, ci) => {
        if (Array.isArray(cat.skills)) {
          const arr = cat.skills as string[];
          if (arr.some((v) => v && v.trim())) {
            fields.push({
              path: ['categories', ci, 'skills'],
              text: JSON.stringify(arr), // ★ 整数组 JSON 发送
              kind: 'array',
            });
          }
        }
      });
      break;
    }
    default:
      break;
  }
  return fields;
}

/**
 * 把润色结果写回 content。数组字段按整段路径写回整个数组，元素数量不变。
 */
export function applyPolishedField(
  section: EditorSection,
  path: PathSegment[],
  value: string | string[],
): SectionContent {
  const next = JSON.parse(JSON.stringify(section.content)) as Record<string, unknown>;
  setByPath(next, path, value);
  return next as unknown as SectionContent;
}

export async function polishSectionContent(section: EditorSection): Promise<SectionContent> {
  const fields = extractPolishableFields(section);
  if (fields.length === 0) return section.content;

  const sectionType = mapSectionType(section.type);
  let cur: SectionContent = section.content;

  for (const f of fields) {
    try {
      const resp = await resumeApi.polish({
        sectionType,
        rawText: f.text,
        format: f.kind === 'array' ? 'json' : 'text',
      });
      const polished = resp.data?.data?.polishedText?.trim();
      if (!polished) continue;

      const value: string | string[] = f.kind === 'array' ? parsePolishedArray(polished) : polished;
      cur = applyPolishedField({ ...section, content: cur }, f.path, value);
    } catch (err) {
      console.error('Polish field failed:', section.type, f.path, err);
    }
  }

  // 最后一道兜底：确保数组字段始终是 string[]（与 JadeAI setResume 一致的防线）
  return normalizeSectionContent(section.type, cur) as unknown as SectionContent;
}

export async function polishBatch(
  resumeId: string | number,
  sectionTypes?: ResumeSectionType[],
): Promise<PolishResponse> {
  const resp = await resumeApi.polish({ resumeId, sectionTypes });
  return resp.data.data;
}
