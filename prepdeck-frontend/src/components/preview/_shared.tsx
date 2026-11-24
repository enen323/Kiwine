// Shared rendering engine for the 50 resume templates.
//
// Every template file in ./templates/ is a thin wrapper that calls
// `renderTemplate(resume, PRESETS.<name>)`. This keeps the 50 components
// DRY: layout logic, the per-section content switch, and theme-variable
// handling all live here. Templates differ only by their `TemplatePreset`.
//
// Styling uses inline styles + the `--brand-*` CSS variables injected by
// `buildThemeCSS` (see resume-preview.tsx). No Tailwind (PrepDeck forbids it);
// the look is fully driven by the user theme + each preset's identity.
//
// NOTE: this module intentionally mixes the pure `renderTemplate`/`PRESETS`
// exports with the internal layout/section components below. That trips the
// `react-refresh/only-export-components` rule, but fast-refresh granularity is
// irrelevant for a shared rendering engine, so we disable it here.

/* eslint-disable react-refresh/only-export-components */

import { type CSSProperties, type ReactNode } from 'react';
import type {
  EditorSection,
  ThemeConfig,
  PersonalInfoContent,
  SummaryContent,
  WorkExperienceContent,
  EducationContent,
  SkillsContent,
  SkillCategory,
  ProjectsContent,
  CertificationsContent,
  LanguagesContent,
  CustomContent,
  PublicationContent,
} from '../../types/editor';
import { DEFAULT_THEME } from '../../types/editor';
import { parseSectionContent } from '../../lib/editor-utils';
import { BACKGROUND_TEMPLATES, SIDEBAR_SECTION_TYPES } from '../../lib/constants';
import type { TemplateResume } from '../../types/preview';
import { AvatarImage } from './avatar-image';

/* ===========================================================================
 * Text / content helpers (ported from JadeAI's preview/utils.ts)
 * ======================================================================== */

/** Lightweight markdown → HTML for resume text (**, `code`, lists, breaks). */
export function md(text: unknown): string {
  if (text == null) return '';
  let s = String(text);
  s = s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  s = s.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  s = s.replace(/`([^`]+)`/g, '<code>$1</code>');
  if (!s.includes('\n')) return s;
  const lines = s.split('\n');
  let html = '';
  let inList = false;
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) {
      if (inList) { html += '</ul>'; inList = false; }
      continue;
    }
    const lm = line.match(/^[-–•]\s+(.*)/);
    if (lm) {
      if (!inList) { html += '<ul style="margin:2px 0;padding-left:1.4em;list-style-type:disc">'; inList = true; }
      html += `<li>${lm[1]}</li>`;
    } else {
      if (inList) { html += '</ul>'; inList = false; }
      html += (html && !html.endsWith('>') ? '<br>' : '') + line;
    }
  }
  if (inList) html += '</ul>';
  return html;
}

export function degreeField(degree: string, field?: string): string {
  if (!field) return degree;
  return `${degree} - ${field}`;
}

export function isSectionEmpty(section: EditorSection): boolean {
  const content = section.content as any;
  if (!content || typeof content !== 'object') return true;
  if (section.type === 'summary') return !(content as SummaryContent).text;
  if (section.type === 'skills') {
    const cats = (content as SkillsContent).categories;
    return !cats?.length || cats.every((c: any) => !c.skills?.length);
  }
  if ('items' in content) return !content.items?.length;
  return false;
}

/* ===========================================================================
 * Preset system — each template is a preset consumed by renderTemplate
 * ======================================================================== */

export type LayoutMode = 'single' | 'centered' | 'two-column' | 'sidebar' | 'timeline' | 'cards' | 'mosaic';
export type AccentStyle = 'underline' | 'bar' | 'box' | 'left-border' | 'dot' | 'none';
export type Signature = 'none' | 'gradient-header' | 'neon' | 'watercolor' | 'zigzag' | 'ribbon' | 'boxed';

export interface TemplatePreset {
  layout: LayoutMode;
  headerAlign?: 'left' | 'center';
  accent?: AccentStyle;
  density?: 'compact' | 'comfortable' | 'spacious';
  brand?: { primary?: string; accent?: string; font?: string };
  sidebarBg?: string;
  sidebarText?: string;
  sidebarWidth?: string;
  cornerRadius?: number;
  paperBg?: string;
  cardBg?: string;
  showAvatar?: boolean;
  signature?: Signature;
}

const CONTACT_FIELDS: [string, keyof PersonalInfoContent][] = [
  ['年龄', 'age'], ['政治面貌', 'politicalStatus'], ['性别', 'gender'], ['民族', 'ethnicity'],
  ['籍贯', 'hometown'], ['婚姻', 'maritalStatus'], ['经验', 'yearsOfExperience'], ['学历', 'educationLevel'],
  ['邮箱', 'email'], ['电话', 'phone'], ['微信', 'wechat'], ['地址', 'location'], ['网站', 'website'],
  ['LinkedIn', 'linkedin'], ['GitHub', 'github'],
];

export const PRESETS: Record<string, TemplatePreset> = {
  classic: { layout: 'single', headerAlign: 'center', accent: 'underline' },
  modern: { layout: 'single', headerAlign: 'left', accent: 'bar', brand: { primary: '#0f172a', accent: '#6366f1' } },
  minimal: { layout: 'single', headerAlign: 'left', accent: 'none', density: 'spacious', brand: { primary: '#27272a', accent: '#a1a1aa' } },
  professional: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#1e3a8a', accent: '#1e3a8a' } },
  'two-column': { layout: 'two-column', accent: 'underline', sidebarBg: '#16213e', sidebarWidth: '35%' },
  creative: { layout: 'centered', accent: 'none', signature: 'gradient-header', brand: { primary: '#0d9488', accent: '#0d9488' }, paperBg: '#f0fdfa' },
  ats: { layout: 'single', headerAlign: 'left', accent: 'none', density: 'compact', brand: { primary: '#222222', accent: '#222222' } },
  academic: { layout: 'single', headerAlign: 'left', accent: 'underline', brand: { primary: '#7c2d12', accent: '#7c2d12' } },
  elegant: { layout: 'single', headerAlign: 'center', accent: 'underline', brand: { primary: '#1c1917', accent: '#b45309' } },
  executive: { layout: 'two-column', accent: 'box', sidebarBg: '#0f172a', sidebarWidth: '36%' },
  developer: { layout: 'two-column', accent: 'left-border', sidebarBg: '#0d1117', sidebarWidth: '34%', brand: { primary: '#e6edf3', accent: '#58a6ff' } },
  designer: { layout: 'two-column', accent: 'box', sidebarBg: '#831843', sidebarWidth: '34%', brand: { primary: '#db2777', accent: '#db2777' } },
  startup: { layout: 'two-column', accent: 'bar', sidebarBg: '#1e293b', sidebarWidth: '34%', brand: { primary: '#f59e0b', accent: '#f59e0b' } },
  formal: { layout: 'single', headerAlign: 'center', accent: 'underline', brand: { primary: '#1e293b', accent: '#1e293b' } },
  infographic: { layout: 'two-column', accent: 'bar', sidebarBg: '#1e3a8a', sidebarWidth: '34%' },
  compact: { layout: 'single', headerAlign: 'left', accent: 'none', density: 'compact', brand: { primary: '#475569', accent: '#475569' } },
  euro: { layout: 'single', headerAlign: 'left', accent: 'underline', brand: { primary: '#1f2937', accent: '#1f2937' } },
  clean: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#0ea5e9', accent: '#0ea5e9' } },
  bold: { layout: 'single', headerAlign: 'center', accent: 'box', brand: { primary: '#020617', accent: '#e11d48' }, density: 'comfortable' },
  timeline: { layout: 'timeline', accent: 'dot', brand: { primary: '#2563eb', accent: '#2563eb' } },
  nordic: { layout: 'single', headerAlign: 'left', accent: 'none', density: 'spacious', brand: { primary: '#0f766e', accent: '#0f766e' } },
  corporate: { layout: 'two-column', accent: 'underline', sidebarBg: '#1e40af', sidebarWidth: '35%' },
  consultant: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#334155', accent: '#334155' } },
  finance: { layout: 'two-column', accent: 'bar', sidebarBg: '#0f4c81', sidebarWidth: '35%' },
  medical: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#0e7490', accent: '#0e7490' } },
  gradient: { layout: 'centered', accent: 'bar', signature: 'gradient-header', brand: { primary: '#7c3aed', accent: '#7c3aed' }, paperBg: '#faf5ff' },
  metro: { layout: 'single', headerAlign: 'left', accent: 'bar', brand: { primary: '#0891b2', accent: '#0891b2' } },
  material: { layout: 'single', headerAlign: 'left', accent: 'box', brand: { primary: '#1565c0', accent: '#1565c0' }, cornerRadius: 8, cardBg: '#f5f8ff' },
  coder: { layout: 'two-column', accent: 'left-border', sidebarBg: '#0d1117', sidebarWidth: '32%', brand: { primary: '#e6edf3', accent: '#58a6ff' } },
  blocks: { layout: 'single', headerAlign: 'left', accent: 'box', signature: 'zigzag', brand: { primary: '#f97316', accent: '#f97316' } },
  magazine: { layout: 'single', headerAlign: 'center', accent: 'underline', brand: { primary: '#be123c', accent: '#be123c' } },
  artistic: { layout: 'centered', accent: 'none', signature: 'watercolor', brand: { primary: '#db2777', accent: '#db2777' }, paperBg: '#fff1f7' },
  retro: { layout: 'single', headerAlign: 'left', accent: 'underline', brand: { primary: '#b45309', accent: '#b45309' } },
  neon: { layout: 'single', headerAlign: 'center', accent: 'box', signature: 'neon', brand: { primary: '#22d3ee', accent: '#22d3ee' }, paperBg: '#0a0a0a', cornerRadius: 6 },
  watercolor: { layout: 'single', headerAlign: 'center', accent: 'underline', signature: 'watercolor', brand: { primary: '#ec4899', accent: '#ec4899' }, paperBg: '#fff1f7' },
  swiss: { layout: 'single', headerAlign: 'left', accent: 'bar', brand: { primary: '#dc2626', accent: '#dc2626' }, density: 'comfortable' },
  japanese: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#b91c1c', accent: '#b91c1c' } },
  berlin: { layout: 'single', headerAlign: 'left', accent: 'bar', brand: { primary: '#1d4ed8', accent: '#1d4ed8' } },
  luxe: { layout: 'centered', headerAlign: 'center', accent: 'underline', brand: { primary: '#92400e', accent: '#92400e' }, paperBg: '#fffaf0' },
  rose: { layout: 'centered', headerAlign: 'center', accent: 'box', brand: { primary: '#e11d48', accent: '#e11d48' }, paperBg: '#fff1f2' },
  architect: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#1e3a8a', accent: '#1e3a8a' } },
  legal: { layout: 'single', headerAlign: 'left', accent: 'underline', brand: { primary: '#1e293b', accent: '#1e293b' } },
  teacher: { layout: 'single', headerAlign: 'left', accent: 'left-border', brand: { primary: '#15803d', accent: '#15803d' } },
  scientist: { layout: 'single', headerAlign: 'left', accent: 'underline', brand: { primary: '#0369a1', accent: '#0369a1' } },
  engineer: { layout: 'two-column', accent: 'left-border', sidebarBg: '#172554', sidebarWidth: '34%', brand: { primary: '#60a5fa', accent: '#60a5fa' } },
  sidebar: { layout: 'sidebar', accent: 'box', sidebarBg: '#1e40af', sidebarWidth: '35%' },
  card: { layout: 'cards', accent: 'left-border', cornerRadius: 10, cardBg: '#f8fafc', brand: { primary: '#7c3aed', accent: '#7c3aed' } },
  zigzag: { layout: 'single', headerAlign: 'center', accent: 'bar', signature: 'zigzag', brand: { primary: '#f59e0b', accent: '#f59e0b' } },
  ribbon: { layout: 'two-column', accent: 'box', sidebarBg: '#9d174d', sidebarWidth: '34%', signature: 'ribbon' },
  mosaic: { layout: 'mosaic', accent: 'none', cornerRadius: 8, cardBg: '#f0fdfa', brand: { primary: '#0d9488', accent: '#0d9488' } },
};

/* ===========================================================================
 * Bridge: normalize a resume (string or structured content) for templates
 * ======================================================================== */

export function toTemplateResume(resume: {
  id?: string | number;
  title?: string;
  template?: string;
  language?: string;
  themeConfig?: ThemeConfig | null;
  sections?: EditorSection[];
}): TemplateResume {
  const themeConfig = { ...DEFAULT_THEME, ...(resume.themeConfig || {}) } as ThemeConfig;
  const sections: EditorSection[] = (resume.sections || []).map((s) => ({
    ...s,
    content: parseSectionContent(s.type, s.content),
  }));
  return {
    id: resume.id,
    title: resume.title,
    template: resume.template,
    language: resume.language,
    themeConfig,
    sections,
  };
}

/* ===========================================================================
 * Small presentational helpers
 * ======================================================================== */

const DENSITY_GAP: Record<string, number> = { compact: 10, comfortable: 16, spacious: 24 };

function contactChips(pi: PersonalInfoContent): string[] {
  const out: string[] = [];
  for (const [label, key] of CONTACT_FIELDS) {
    const v = pi[key];
    if (typeof v === 'string' && v.trim()) out.push(`${label}：${v}`);
  }
  if (Array.isArray(pi.customLinks)) {
    for (const link of pi.customLinks) {
      if (link?.url) out.push(`${link.label || '链接'}：${link.url}`);
    }
  }
  return out;
}

function SectionTitle({ title, preset, onDark }: { title: string; preset: TemplatePreset; onDark?: boolean }) {
  const accent = preset.accent ?? 'underline';
  const color = onDark ? '#ffffff' : 'var(--brand-primary)';
  const accentColor = preset.brand?.accent ?? 'var(--brand-accent)';
  const radius = preset.cornerRadius ?? 3;

  if (accent === 'box') {
    return (
      <h2 style={{ fontSize: 'var(--brand-h2)', fontWeight: 700, color: '#fff', background: accentColor, display: 'inline-block', padding: '2px 10px', borderRadius: radius, margin: '0 0 8px', letterSpacing: '0.03em' }}>
        {title}
      </h2>
    );
  }
  if (accent === 'left-border') {
    return (
      <h2 style={{ fontSize: 'var(--brand-h2)', fontWeight: 700, color, borderLeft: `3px solid ${accentColor}`, paddingLeft: 8, margin: '0 0 8px', letterSpacing: '0.03em' }}>
        {title}
      </h2>
    );
  }
  if (accent === 'bar') {
    return (
      <h2 style={{ fontSize: 'var(--brand-h2)', fontWeight: 700, color, margin: '0 0 8px', display: 'flex', alignItems: 'center', gap: 8, letterSpacing: '0.03em' }}>
        <span style={{ width: 18, height: 4, background: accentColor, display: 'inline-block', borderRadius: 2 }} />
        {title}
      </h2>
    );
  }
  if (accent === 'dot') {
    return (
      <h2 style={{ fontSize: 'var(--brand-h2)', fontWeight: 700, color, margin: '0 0 8px', display: 'flex', alignItems: 'center', gap: 8, letterSpacing: '0.03em' }}>
        <span style={{ width: 9, height: 9, borderRadius: '50%', background: accentColor, display: 'inline-block' }} />
        {title}
      </h2>
    );
  }
  // underline (default) & none
  return (
    <h2 style={{ fontSize: 'var(--brand-h2)', fontWeight: 700, color, borderBottom: accent === 'none' ? 'none' : `2px solid ${accentColor}`, paddingBottom: accent === 'none' ? 0 : 4, margin: '0 0 8px', letterSpacing: '0.03em', textTransform: 'uppercase' }}>
      {title}
    </h2>
  );
}

function HeaderBlock({ pi, preset, align, onDark }: {
  pi: PersonalInfoContent; preset: TemplatePreset; align: 'left' | 'center'; onDark?: boolean;
}) {
  const nameColor = onDark ? '#ffffff' : 'var(--brand-primary)';
  const subColor = onDark ? 'rgba(255,255,255,0.78)' : '#5b5b5b';
  const contactColor = onDark ? 'rgba(255,255,255,0.85)' : '#777777';
  const center = align === 'center';
  const showAvatar = preset.showAvatar ?? !!pi.avatar;
  const signature = preset.signature;

  const nameNode = (
    <>
      <h1 style={{ fontSize: 'var(--brand-h1)', fontWeight: 700, margin: 0, color: nameColor, lineHeight: 1.2 }}>{pi.fullName || '姓名'}</h1>
      {pi.jobTitle && <p style={{ margin: '4px 0 0', fontSize: 'var(--brand-h3)', color: subColor }}>{pi.jobTitle}</p>}
    </>
  );

  const contacts = contactChips(pi);

  // Full-bleed header bands for signature templates
  if (signature === 'gradient-header') {
    const g = `linear-gradient(120deg, ${preset.brand?.primary ?? '#0d9488'}, ${preset.brand?.accent ?? '#0d9488'})`;
    return (
      <div style={{ marginBottom: 'calc(var(--brand-section-gap) * 1.1)', padding: '28px 32px', background: g, color: '#fff', borderRadius: preset.cornerRadius ?? 6, textAlign: 'center' }}>
        <h1 style={{ fontSize: 'var(--brand-h1)', fontWeight: 700, margin: 0, color: '#fff', lineHeight: 1.2 }}>{pi.fullName || '姓名'}</h1>
        {pi.jobTitle && <p style={{ margin: '6px 0 0', fontSize: 'var(--brand-h3)', color: 'rgba(255,255,255,0.9)' }}>{pi.jobTitle}</p>}
        {contacts.length > 0 && (
          <div style={{ marginTop: 10, display: 'flex', flexWrap: 'wrap', gap: '4px 14px', justifyContent: 'center', fontSize: '0.8em', color: 'rgba(255,255,255,0.92)' }}>
            {contacts.map((c, i) => <span key={i}>{c}</span>)}
          </div>
        )}
      </div>
    );
  }

  const headerInner = (
    <div style={{ marginBottom: 'calc(var(--brand-section-gap) * 1.1)', textAlign: center ? 'center' : 'left' }}>
      {showAvatar && pi.avatar && (
        <div style={{ marginBottom: 10, ...(center ? { display: 'flex', justifyContent: 'center' } : {}) }}>
          <AvatarImage src={pi.avatar} avatarStyle={preset.brand?.primary ? 'circle' : 'oneInch'} size={center ? 76 : 64} />
        </div>
      )}
      {nameNode}
      <div style={{ marginTop: 8, display: 'flex', flexWrap: 'wrap', gap: '4px 12px', fontSize: '0.82em', color: contactColor, ...(center ? { justifyContent: 'center' } : {}) }}>
        {contacts.map((c, i) => <span key={i}>{c}</span>)}
      </div>
      {signature === 'zigzag' && (
        <div style={{ marginTop: 10, height: 8, background: `repeating-linear-gradient(135deg, ${preset.brand?.accent ?? '#f59e0b'} 0 8px, transparent 8px 16px)` }} />
      )}
      {signature === 'ribbon' && (
        <div style={{ marginTop: 10, display: 'inline-block', background: preset.brand?.accent ?? '#db2777', color: '#fff', padding: '3px 14px', borderRadius: 999, fontSize: '0.8em' }}>{pi.jobTitle || '简历'}</div>
      )}
    </div>
  );

  if (signature === 'neon') {
    return <div style={{ marginBottom: 'calc(var(--brand-section-gap) * 1.1)', textAlign: center ? 'center' : 'left' }}>{nameNode}</div>;
  }
  return headerInner;
}

/** Render one section's body. `variant` controls density for sidebar columns. */
function SectionBody({ section, lang, variant = 'full' }: { section: EditorSection; lang?: string; variant?: 'full' | 'compact' }) {
  const content = section.content as any;
  if (!content) return null;
  const present = (v: unknown) => v !== undefined && v !== null && v !== '';
  const bodyColor = '#4b4b4b';

  if (section.type === 'summary') {
    return (
      <p style={{ color: bodyColor, fontSize: 'var(--brand-body)', lineHeight: 'var(--brand-line-height)', margin: 0 }} dangerouslySetInnerHTML={{ __html: md((content as SummaryContent).text) }} />
    );
  }

  if (section.type === 'experience') {
    const items = (content as WorkExperienceContent).items || [];
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {items.map((item: any) => (
          <div key={item.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
              <div>
                <span style={{ fontWeight: 600, color: 'var(--brand-primary)' }}>{item.position}</span>
                {present(item.company) && <span style={{ color: '#555' }}> · {item.company}</span>}
                {present(item.location) && <span style={{ color: '#999', fontSize: '0.85em' }}> , {item.location}</span>}
              </div>
              <span style={{ color: '#999', fontSize: '0.8em', whiteSpace: 'nowrap' }}>{item.startDate} - {item.endDate || (item.current ? (lang === 'zh' ? '至今' : 'Present') : '')}</span>
            </div>
            {present(item.description) && <p style={{ margin: '4px 0 0', color: bodyColor, fontSize: 'var(--brand-body)' }} dangerouslySetInnerHTML={{ __html: md(item.description) }} />}
            {item.technologies?.length > 0 && <p style={{ margin: '3px 0 0', color: '#999', fontSize: '0.8em' }}>{lang === 'zh' ? '技术栈' : 'Tech'}: {item.technologies.join(', ')}</p>}
            {item.highlights?.length > 0 && (
              <ul style={{ margin: '4px 0 0', paddingLeft: '1.3em', color: bodyColor, fontSize: 'var(--brand-body)' }}>
                {item.highlights.map((h: string, i: number) => <li key={i} dangerouslySetInnerHTML={{ __html: md(h) }} />)}
              </ul>
            )}
          </div>
        ))}
      </div>
    );
  }

  if (section.type === 'education') {
    const items = (content as EducationContent).items || [];
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {items.map((item: any) => (
          <div key={item.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 600, color: 'var(--brand-primary)' }}>{item.institution}</span>
              <span style={{ color: '#999', fontSize: '0.8em', whiteSpace: 'nowrap' }}>{item.startDate} - {item.endDate || (lang === 'zh' ? '至今' : 'Present')}</span>
            </div>
            <p style={{ margin: '2px 0 0', color: bodyColor, fontSize: 'var(--brand-body)' }}>{degreeField(item.degree, item.field)}</p>
            {present(item.gpa) && <p style={{ margin: '1px 0 0', color: '#888', fontSize: '0.82em' }}>GPA: {item.gpa}</p>}
            {item.highlights?.length > 0 && (
              <ul style={{ margin: '4px 0 0', paddingLeft: '1.3em', color: bodyColor, fontSize: 'var(--brand-body)' }}>
                {item.highlights.map((h: string, i: number) => <li key={i} dangerouslySetInnerHTML={{ __html: md(h) }} />)}
              </ul>
            )}
          </div>
        ))}
      </div>
    );
  }

  if (section.type === 'project') {
    const items = (content as ProjectsContent).items || [];
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {items.map((item: any) => (
          <div key={item.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ fontWeight: 600, color: 'var(--brand-primary)' }}>{item.name}</span>
              {present(item.startDate) && <span style={{ color: '#999', fontSize: '0.8em', whiteSpace: 'nowrap' }}>{item.startDate} - {item.endDate || (lang === 'zh' ? '至今' : 'Present')}</span>}
            </div>
            {present(item.description) && <p style={{ margin: '4px 0 0', color: bodyColor, fontSize: 'var(--brand-body)' }} dangerouslySetInnerHTML={{ __html: md(item.description) }} />}
            {item.technologies?.length > 0 && <p style={{ margin: '3px 0 0', color: '#999', fontSize: '0.8em' }}>{lang === 'zh' ? '技术栈' : 'Tech'}: {item.technologies.join(', ')}</p>}
            {item.highlights?.length > 0 && (
              <ul style={{ margin: '4px 0 0', paddingLeft: '1.3em', color: bodyColor, fontSize: 'var(--brand-body)' }}>
                {item.highlights.map((h: string, i: number) => <li key={i} dangerouslySetInnerHTML={{ __html: md(h) }} />)}
              </ul>
            )}
          </div>
        ))}
      </div>
    );
  }

  if (section.type === 'skills') {
    const categories = (content as SkillsContent).categories || [];
    if (variant === 'compact') {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          {categories.map((cat: SkillCategory) => (
            <div key={cat.id}>
              <p style={{ margin: 0, fontSize: '0.82em', fontWeight: 600, color: 'var(--brand-primary)' }}>{cat.name}</p>
              <div style={{ marginTop: 3, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {(cat.skills || []).map((skill: string, i: number) => (
                  <span key={i} style={{ background: 'var(--brand-accent)', color: '#fff', borderRadius: 3, padding: '1px 6px', fontSize: '0.72em', opacity: 0.92 }}>{skill}</span>
                ))}
              </div>
            </div>
          ))}
        </div>
      );
    }
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {categories.map((cat: SkillCategory) => (
          <div
            key={cat.id}
            style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'baseline', gap: '4px 6px', fontSize: 'var(--brand-body)' }}
          >
            {cat.name ? (
              <span style={{ fontWeight: 500, color: '#555', flexShrink: 1, minWidth: 0, wordBreak: 'break-word' }}>
                {cat.name}：
              </span>
            ) : null}
            <span style={{ color: bodyColor, flex: 1, minWidth: 0, wordBreak: 'break-word' }}>
              {(cat.skills || []).join('，')}
            </span>
          </div>
        ))}
      </div>
    );
  }

  if (section.type === 'language') {
    const items = (content as LanguagesContent).items || [];
    if (variant === 'compact') {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {items.map((item: any) => (
            <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.82em' }}>
              <span style={{ color: 'var(--brand-primary)', fontWeight: 500 }}>{item.language}</span>
              <span style={{ color: '#888' }}>{item.proficiency}</span>
            </div>
          ))}
        </div>
      );
    }
    return (
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px 18px' }}>
        {items.map((item: any) => (
          <span key={item.id} style={{ fontSize: 'var(--brand-body)' }}>
            <span style={{ fontWeight: 500, color: 'var(--brand-primary)' }}>{item.language}</span>
            <span style={{ color: '#666' }}> — {item.proficiency}</span>
          </span>
        ))}
      </div>
    );
  }

  if (section.type === 'certification') {
    const items = (content as CertificationsContent).items || [];
    if (variant === 'compact') {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {items.map((item: any) => (
            <div key={item.id}>
              <p style={{ margin: 0, fontSize: '0.82em', fontWeight: 600, color: 'var(--brand-primary)' }}>{item.name}</p>
              {(item.issuer || item.date) && <p style={{ margin: 0, fontSize: '0.72em', color: '#888' }}>{item.issuer}{item.date ? ` (${item.date})` : ''}</p>}
            </div>
          ))}
        </div>
      );
    }
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
        {items.map((item: any) => (
          <div key={item.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--brand-body)' }}>
            <span><span style={{ fontWeight: 600, color: 'var(--brand-primary)' }}>{item.name}</span>{item.issuer && <span style={{ color: '#666' }}> — {item.issuer}</span>}</span>
            {item.date && <span style={{ color: '#999', fontSize: '0.85em' }}>{item.date}</span>}
          </div>
        ))}
      </div>
    );
  }

  if (section.type === 'custom') {
    const items = (content as CustomContent).items || [];
    if (variant === 'compact') {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
          {items.map((item: any) => (
            <div key={item.id}>
              <p style={{ margin: 0, fontSize: '0.82em', fontWeight: 600, color: 'var(--brand-primary)' }}>{item.title}</p>
              {present(item.subtitle) && <p style={{ margin: 0, fontSize: '0.72em', color: '#888' }}>{item.subtitle}</p>}
              {present(item.description) && <p style={{ margin: 0, fontSize: '0.72em', color: '#777' }} dangerouslySetInnerHTML={{ __html: md(item.description) }} />}
            </div>
          ))}
        </div>
      );
    }
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {items.map((item: any) => (
          <div key={item.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8 }}>
              <span style={{ fontSize: 'var(--brand-body)', fontWeight: 600, color: 'var(--brand-primary)' }}>{item.title}</span>
              {present(item.date) && <span style={{ color: '#999', fontSize: '0.82em' }}>{item.date}</span>}
            </div>
            {present(item.subtitle) && <p style={{ margin: '1px 0 0', color: '#666', fontSize: '0.85em' }}>{item.subtitle}</p>}
            {present(item.description) && <p style={{ margin: '3px 0 0', color: bodyColor, fontSize: 'var(--brand-body)' }} dangerouslySetInnerHTML={{ __html: md(item.description) }} />}
          </div>
        ))}
      </div>
    );
  }

  if (section.type === 'publication') {
    const items = (content as PublicationContent).items || [];
    if (variant === 'compact') {
      return (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          {items.map((item: any) => (
            <div key={item.id}>
              <p style={{ margin: 0, fontSize: '0.82em', fontWeight: 600, color: 'var(--brand-primary)' }}>{item.title}</p>
              {(item.venue || item.date) && <p style={{ margin: 0, fontSize: '0.72em', color: '#888' }}>{item.venue}{item.date ? ` (${item.date})` : ''}</p>}
            </div>
          ))}
        </div>
      );
    }
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {items.map((item: any) => (
          <div key={item.id}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 8, flexWrap: 'wrap' }}>
              <span style={{ fontSize: 'var(--brand-body)', fontWeight: 600, color: 'var(--brand-primary)' }}>{item.title}</span>
              {present(item.date) && <span style={{ color: '#999', fontSize: '0.82em', whiteSpace: 'nowrap' }}>{item.date}</span>}
            </div>
            {present(item.authors) && <p style={{ margin: '1px 0 0', color: '#666', fontSize: '0.85em' }}>{item.authors}</p>}
            {present(item.venue) && <p style={{ margin: 0, fontSize: '0.8em', color: '#999' }}>{item.venue}</p>}
            {present(item.description) && <p style={{ margin: '3px 0 0', color: bodyColor, fontSize: 'var(--brand-body)' }} dangerouslySetInnerHTML={{ __html: md(item.description) }} />}
          </div>
        ))}
      </div>
    );
  }

  // Generic fallback for any other structured list
  if (content?.items) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        {content.items.map((item: any) => (
          <div key={item.id}>
            <span style={{ fontSize: 'var(--brand-body)', fontWeight: 500, color: 'var(--brand-primary)' }}>{item.name || item.title || item.language}</span>
            {present(item.description) && <p style={{ margin: '2px 0 0', color: bodyColor, fontSize: 'var(--brand-body)' }} dangerouslySetInnerHTML={{ __html: md(item.description) }} />}
          </div>
        ))}
      </div>
    );
  }

  return null;
}

/* ===========================================================================
 * Layout renderers
 * ======================================================================== */

function SectionBlock({ section, preset, lang, variant, onDark }: {
  section: EditorSection; preset: TemplatePreset; lang?: string; variant?: 'full' | 'compact'; onDark?: boolean;
}) {
  const gap = DENSITY_GAP[preset.density ?? 'comfortable'];
  return (
    <div data-section style={{ marginBottom: gap }}>
      <SectionTitle title={section.title} preset={preset} onDark={onDark} />
      <SectionBody section={section} lang={lang} variant={variant} />
    </div>
  );
}

function SingleLayout({ preset, pi, sections, lang, align, innerPad }: {
  preset: TemplatePreset; pi: PersonalInfoContent; sections: EditorSection[]; lang?: string; align: 'left' | 'center'; innerPad: string;
}) {
  const onDark = preset.signature === 'neon';
  const textColor = onDark ? '#e6e6e6' : '#333';
  return (
    <div style={{ padding: innerPad, color: textColor }}>
      <HeaderBlock pi={pi} preset={preset} align={align} onDark={onDark} />
      {sections.map((s) => <SectionBlock key={s.id} section={s} preset={preset} lang={lang} />)}
    </div>
  );
}

function TimelineLayout({ preset, pi, sections, lang, innerPad }: {
  preset: TemplatePreset; pi: PersonalInfoContent; sections: EditorSection[]; lang?: string; innerPad: string;
}) {
  return (
    <div style={{ padding: innerPad }}>
      <HeaderBlock pi={pi} preset={preset} align="left" />
      <div style={{ borderLeft: `2px solid var(--brand-accent)`, marginLeft: 6, paddingLeft: 18 }}>
        {sections.map((s) => {
          const p = { ...preset, accent: 'dot' as AccentStyle };
          return (
            <div key={s.id} style={{ position: 'relative', marginBottom: DENSITY_GAP[preset.density ?? 'comfortable'] }}>
              <span style={{ position: 'absolute', left: -25, top: 4, width: 11, height: 11, borderRadius: '50%', background: 'var(--brand-accent)', border: '2px solid #fff' }} />
              <SectionTitle title={s.title} preset={p} />
              <SectionBody section={s} lang={lang} />
            </div>
          );
        })}
      </div>
    </div>
  );
}

function CardsLayout({ preset, pi, sections, lang, innerPad }: {
  preset: TemplatePreset; pi: PersonalInfoContent; sections: EditorSection[]; lang?: string; innerPad: string;
}) {
  const radius = preset.cornerRadius ?? 10;
  const cardBg = preset.cardBg ?? '#ffffff';
  return (
    <div style={{ padding: innerPad }}>
      <HeaderBlock pi={pi} preset={preset} align="left" />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {sections.map((s) => (
          <div key={s.id} style={{ background: cardBg, borderRadius: radius, padding: 16, boxShadow: '0 1px 3px rgba(0,0,0,0.08)', border: '1px solid rgba(0,0,0,0.05)' }}>
            <SectionTitle title={s.title} preset={preset} />
            <SectionBody section={s} lang={lang} />
          </div>
        ))}
      </div>
    </div>
  );
}

function MosaicLayout({ preset, pi, sections, lang, innerPad }: {
  preset: TemplatePreset; pi: PersonalInfoContent; sections: EditorSection[]; lang?: string; innerPad: string;
}) {
  const radius = preset.cornerRadius ?? 8;
  const cardBg = preset.cardBg ?? '#f0fdfa';
  return (
    <div style={{ padding: innerPad }}>
      <HeaderBlock pi={pi} preset={preset} align="center" />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 12 }}>
        {sections.map((s) => (
          <div key={s.id} style={{ background: cardBg, borderRadius: radius, padding: 14, border: '1px solid rgba(0,0,0,0.05)' }}>
            <SectionTitle title={s.title} preset={preset} />
            <SectionBody section={s} lang={lang} />
          </div>
        ))}
      </div>
    </div>
  );
}

function TwoColumnLayout({ preset, pi, sections, lang, innerPad }: {
  preset: TemplatePreset; pi: PersonalInfoContent; sections: EditorSection[]; lang?: string; innerPad: string;
}) {
  const bg = preset.sidebarBg ?? '#16213e';
  const width = preset.sidebarWidth ?? '35%';
  const leftSections = sections.filter((s) => SIDEBAR_SECTION_TYPES.has(s.type));
  const rightSections = sections.filter((s) => !SIDEBAR_SECTION_TYPES.has(s.type));

  return (
    <div style={{ display: 'flex', minHeight: '297mm', background: '#fff' }}>
      <div style={{ width, flexShrink: 0, background: bg, color: '#fff', padding: 24 }}>
        <div style={{ textAlign: 'center', marginBottom: 18 }}>
          {pi.avatar && <div style={{ marginBottom: 10, display: 'flex', justifyContent: 'center' }}><AvatarImage src={pi.avatar} avatarStyle="circle" size={80} /></div>}
          <h1 style={{ fontSize: 'var(--brand-h1)', fontWeight: 700, margin: 0, color: '#fff', lineHeight: 1.2 }}>{pi.fullName || '姓名'}</h1>
          {pi.jobTitle && <p style={{ margin: '6px 0 0', fontSize: 'var(--brand-h3)', color: 'rgba(255,255,255,0.8)' }}>{pi.jobTitle}</p>}
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: '0.78em', color: 'rgba(255,255,255,0.85)', marginBottom: 18 }}>
          {contactChips(pi).map((c, i) => <div key={i}>{c}</div>)}
        </div>
        {leftSections.map((s) => <SectionBlock key={s.id} section={s} preset={preset} lang={lang} variant="compact" onDark />)}
      </div>
      <div style={{ flex: 1, padding: innerPad }}>
        {rightSections.map((s) => <SectionBlock key={s.id} section={s} preset={preset} lang={lang} />)}
      </div>
    </div>
  );
}

/* ===========================================================================
 * Public entry: render a resume with a given preset
 * ======================================================================== */

export function renderTemplate(resume: TemplateResume, preset: TemplatePreset): ReactNode {
  const lang = resume.language;
  const pi = (resume.sections.find((s) => s.type === 'personalInfo')?.content || {}) as PersonalInfoContent;
  const visible = resume.sections.filter(
    (s) => s.visible && s.type !== 'personalInfo' && !isSectionEmpty(s)
  );
  const tpl = resume.template || 'classic';
  const fullBleed = BACKGROUND_TEMPLATES.has(tpl);
  const innerPad = fullBleed
    ? 'var(--brand-margin-top) var(--brand-margin-right) var(--brand-margin-bottom) var(--brand-margin-left)'
    : '0';

  const rootStyle: CSSProperties = {
    maxWidth: '210mm',
    margin: '0 auto',
    background: preset.paperBg || '#ffffff',
    minHeight: '297mm',
    position: 'relative',
    fontFamily: 'var(--brand-font)',
    boxSizing: 'border-box',
  };

  let body: ReactNode;
  switch (preset.layout) {
    case 'two-column':
    case 'sidebar':
      body = <TwoColumnLayout preset={preset} pi={pi} sections={visible} lang={lang} innerPad={innerPad} />;
      break;
    case 'timeline':
      body = <TimelineLayout preset={preset} pi={pi} sections={visible} lang={lang} innerPad={innerPad} />;
      break;
    case 'cards':
      body = <CardsLayout preset={preset} pi={pi} sections={visible} lang={lang} innerPad={innerPad} />;
      break;
    case 'mosaic':
      body = <MosaicLayout preset={preset} pi={pi} sections={visible} lang={lang} innerPad={innerPad} />;
      break;
    case 'centered':
      body = <SingleLayout preset={preset} pi={pi} sections={visible} lang={lang} align="center" innerPad={innerPad} />;
      break;
    case 'single':
    default:
      body = <SingleLayout preset={preset} pi={pi} sections={visible} lang={lang} align={preset.headerAlign || 'left'} innerPad={innerPad} />;
      break;
  }

  return <div className="rp-root" style={rootStyle}>{body}</div>;
}
