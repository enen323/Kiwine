import type {
  SectionContent,
  SectionType,
} from '../types/editor';
import { SECTION_LABELS } from '../types/editor';

/** Generate a UUID, with a manual fallback for non-secure contexts. */
export function generateId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/**
 * Parse a section's `content` payload. The backend stores content as a JSON
 * string, but the in-memory editor expects a structured object. This coerces
 * both forms and repairs corrupted arrays (mirrors JadeAI's normalize logic).
 */
export function parseSectionContent(type: string, content: unknown): SectionContent {
  let obj: Record<string, unknown> = {};

  if (typeof content === 'string') {
    if (!content) {
      obj = {};
    } else {
      try {
        const parsed = JSON.parse(content);
        // 后端可能存储「被 JSON 编码的纯文本字符串」（如 AI 生成内容
        // "使用React全家桶..."）。JSON.parse 会把这种值解析回 *字符串本身*，
        // 而非对象。此时应原样返回，交给下游 normalizeSectionContent 的扁平
        // 文本兜底把它规范化为结构体。若当成对象去挂 .items/.categories，会
        // 抛出 "Cannot create property 'items' on string"。
        if (typeof parsed !== 'object' || parsed === null) {
          return parsed as unknown as SectionContent;
        }
        obj = parsed;
      } catch {
        obj = {};
      }
    }
  } else if (content && typeof content === 'object') {
    obj = content as Record<string, unknown>;
  }

  // Repair list fields so a corrupted resume renders instead of crashing.
  switch (type) {
    case 'experience':
    case 'education':
    case 'project':
    case 'certification':
    case 'language':
    case 'publication':
    case 'custom': {
      if (!Array.isArray(obj.items)) obj.items = [];
      break;
    }
    case 'skills': {
      if (!Array.isArray(obj.categories)) obj.categories = [];
      break;
    }
    default:
      break;
  }
  return obj as unknown as SectionContent;
}

/** Serialize a structured section content into the string form for the API. */
export function serializeSectionContent(content: SectionContent): string {
  return JSON.stringify(content);
}

/**
 * Build the default empty content for a freshly added section.
 */
export function defaultContentForType(type: SectionType): SectionContent {
  switch (type) {
    case 'personalInfo':
      return { fullName: '', jobTitle: '', email: '', phone: '', location: '' } as SectionContent;
    case 'summary':
      return { text: '' };
    case 'skills':
      return {
        categories: [
          { id: generateId(), name: '专业技能', skills: ['编程语言', '框架工具', '协作沟通'] },
        ],
      };
    case 'experience':
    case 'education':
    case 'project':
    case 'certification':
    case 'language':
    case 'publication':
    case 'custom':
      return { items: [] };
    default:
      return { items: [] };
  }
}

/**
 * Determine a sensible default title for a section type. Used when adding a
 * section whose backend title is missing.
 */
export function defaultTitleForType(type: SectionType): string {
  return SECTION_LABELS[type] ?? type;
}

/**
 * Maps raw backend section-type strings (in either camelCase or the legacy
 * snake_case form) to their Chinese label. Used to detect an *untranslated*
 * title so it can be swapped for the localized label.
 */
const RAW_TYPE_TO_LABEL: Record<string, string> = {
  personalInfo: '个人信息', personal_info: '个人信息',
  summary: '个人概述',
  education: '教育经历',
  experience: '工作经历', work_experience: '工作经历',
  project: '项目经历', projects: '项目经历',
  skills: '技能',
  certification: '证书', certifications: '证书',
  language: '语言能力', languages: '语言能力',
  publication: '出版物', publications: '出版物',
  custom: '自定义模块',
};

/**
 * Resolve the display title for a section. If the stored `title` is missing or
 * is itself a raw section-type key (i.e. never localized), fall back to the
 * Chinese label for that type. Otherwise keep the title as-is (so genuine
 * custom titles — including Chinese ones — are preserved).
 */
export function normalizeSectionTitle(type: SectionType, title?: string): string {
  if (title && !RAW_TYPE_TO_LABEL[title]) return title;
  return SECTION_LABELS[type] ?? type;
}
