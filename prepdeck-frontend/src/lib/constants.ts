// Central registry of the 6 built-in resume templates.

export const TEMPLATES: string[] = [
  'classic', 'modern', 'minimal', 'professional', 'creative', 'ats',
];

export type Template = (typeof TEMPLATES)[number];

export const TEMPLATE_LABELS: Record<string, string> = {
  classic: '经典', modern: '现代', minimal: '极简', professional: '专业',
  creative: '创意', ats: 'ATS 友好',
};

// Representative accent colour per template — drives the picker thumbnail and
// the template's signature identity when no user theme override is applied.
export const TEMPLATE_ACCENTS: Record<string, string> = {
  classic: '#1a1a1a', modern: '#6366f1', minimal: '#52525b', professional: '#1e3a8a',
  creative: '#0d9488', ats: '#334155',
};

// Templates with a full-bleed background header/sidebar — the outer page
// padding is suppressed so the colour runs edge-to-edge.
export const BACKGROUND_TEMPLATES: ReadonlySet<string> = new Set([
  'modern', 'creative',
]);

// Dual-column templates: dark sidebar configuration (background + width).
export const TWO_COLUMN_TEMPLATES: Record<string, { bg: string; width: string }> = {};

// Which section types are pushed to the (left) sidebar in dual-column layouts.
export const SIDEBAR_SECTION_TYPES: ReadonlySet<string> = new Set([
  'skills', 'language', 'certification', 'custom',
]);

// Picker grouping for the template gallery.
export const TEMPLATE_GROUPS: { id: string; label: string; items: string[] }[] = [
  { id: 'recommended', label: '推荐', items: ['classic', 'modern', 'minimal', 'professional', 'creative', 'ats'] },
];
