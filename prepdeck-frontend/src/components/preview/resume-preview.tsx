import { useId, type ComponentType } from 'react';
import type { EditorSection, ThemeConfig } from '../../types/editor';
import type { TemplateResume } from '../../types/preview';
import { DEFAULT_THEME } from '../../types/editor';
import { BACKGROUND_TEMPLATES } from '../../lib/constants';
import { toTemplateResume } from './_shared';

import { ClassicTemplate } from './templates/classic';
import { ModernTemplate } from './templates/modern';
import { MinimalTemplate } from './templates/minimal';
import { ProfessionalTemplate } from './templates/professional';
import { TwoColumnTemplate } from './templates/two-column';
import { CreativeTemplate } from './templates/creative';
import { AtsTemplate } from './templates/ats';
import { AcademicTemplate } from './templates/academic';
import { ElegantTemplate } from './templates/elegant';
import { ExecutiveTemplate } from './templates/executive';
import { DeveloperTemplate } from './templates/developer';
import { DesignerTemplate } from './templates/designer';
import { StartupTemplate } from './templates/startup';
import { FormalTemplate } from './templates/formal';
import { InfographicTemplate } from './templates/infographic';
import { CompactTemplate } from './templates/compact';
import { EuroTemplate } from './templates/euro';
import { CleanTemplate } from './templates/clean';
import { BoldTemplate } from './templates/bold';
import { TimelineTemplate } from './templates/timeline';
import { NordicTemplate } from './templates/nordic';
import { CorporateTemplate } from './templates/corporate';
import { ConsultantTemplate } from './templates/consultant';
import { FinanceTemplate } from './templates/finance';
import { MedicalTemplate } from './templates/medical';
import { GradientTemplate } from './templates/gradient';
import { MetroTemplate } from './templates/metro';
import { MaterialTemplate } from './templates/material';
import { CoderTemplate } from './templates/coder';
import { BlocksTemplate } from './templates/blocks';
import { MagazineTemplate } from './templates/magazine';
import { ArtisticTemplate } from './templates/artistic';
import { RetroTemplate } from './templates/retro';
import { NeonTemplate } from './templates/neon';
import { WatercolorTemplate } from './templates/watercolor';
import { SwissTemplate } from './templates/swiss';
import { JapaneseTemplate } from './templates/japanese';
import { BerlinTemplate } from './templates/berlin';
import { LuxeTemplate } from './templates/luxe';
import { RoseTemplate } from './templates/rose';
import { ArchitectTemplate } from './templates/architect';
import { LegalTemplate } from './templates/legal';
import { TeacherTemplate } from './templates/teacher';
import { ScientistTemplate } from './templates/scientist';
import { EngineerTemplate } from './templates/engineer';
import { SidebarTemplate } from './templates/sidebar';
import { CardTemplate } from './templates/card';
import { ZigzagTemplate } from './templates/zigzag';
import { RibbonTemplate } from './templates/ribbon';
import { MosaicTemplate } from './templates/mosaic';

type PreviewInput = {
  title?: string;
  template?: string;
  language?: string;
  themeConfig?: ThemeConfig | null;
  sections?: EditorSection[];
};

const templateMap: Record<string, ComponentType<{ resume: TemplateResume }>> = {
  classic: ClassicTemplate,
  modern: ModernTemplate,
  minimal: MinimalTemplate,
  professional: ProfessionalTemplate,
  'two-column': TwoColumnTemplate,
  creative: CreativeTemplate,
  ats: AtsTemplate,
  academic: AcademicTemplate,
  elegant: ElegantTemplate,
  executive: ExecutiveTemplate,
  developer: DeveloperTemplate,
  designer: DesignerTemplate,
  startup: StartupTemplate,
  formal: FormalTemplate,
  infographic: InfographicTemplate,
  compact: CompactTemplate,
  euro: EuroTemplate,
  clean: CleanTemplate,
  bold: BoldTemplate,
  timeline: TimelineTemplate,
  nordic: NordicTemplate,
  corporate: CorporateTemplate,
  consultant: ConsultantTemplate,
  finance: FinanceTemplate,
  medical: MedicalTemplate,
  gradient: GradientTemplate,
  metro: MetroTemplate,
  material: MaterialTemplate,
  coder: CoderTemplate,
  blocks: BlocksTemplate,
  magazine: MagazineTemplate,
  artistic: ArtisticTemplate,
  retro: RetroTemplate,
  neon: NeonTemplate,
  watercolor: WatercolorTemplate,
  swiss: SwissTemplate,
  japanese: JapaneseTemplate,
  berlin: BerlinTemplate,
  luxe: LuxeTemplate,
  rose: RoseTemplate,
  architect: ArchitectTemplate,
  legal: LegalTemplate,
  teacher: TeacherTemplate,
  scientist: ScientistTemplate,
  engineer: EngineerTemplate,
  sidebar: SidebarTemplate,
  card: CardTemplate,
  zigzag: ZigzagTemplate,
  ribbon: RibbonTemplate,
  mosaic: MosaicTemplate,
};

const FONT_SIZE_SCALE: Record<string, { body: string; h1: string; h2: string; h3: string }> = {
  small: { body: '12px', h1: '22px', h2: '15px', h3: '13px' },
  medium: { body: '14px', h1: '26px', h2: '17px', h3: '15px' },
  large: { body: '16px', h1: '30px', h2: '19px', h3: '17px' },
};

/**
 * Inject the resume theme as `--brand-*` CSS variables scoped to the preview,
 * plus the outer page padding for non-full-bleed templates. Templates consume
 * these variables via inline styles. This is intentional runtime theming (the
 * same idea as JadeAI's `--brand` system): per-resume colors are dynamic, so
 * they live in CSS variables rather than static Tailwind utilities.
 */
function buildThemeCSS(scopeId: string, theme: ThemeConfig, template: string): string {
  const s = `[data-theme-scope="${scopeId}"]`;
  const fs = FONT_SIZE_SCALE[theme.fontSize] || FONT_SIZE_SCALE.medium;
  const m = theme.margin;
  const needsPadding = !BACKGROUND_TEMPLATES.has(template);
  const primary = theme.primaryColor;
  const accent = theme.accentColor;

  return `
    ${s} {
      --brand-primary: ${primary};
      --brand-accent: ${accent};
      --brand-font: ${theme.fontFamily}, 'Noto Sans SC', sans-serif;
      --brand-body: ${fs.body};
      --brand-h1: ${fs.h1};
      --brand-h2: ${fs.h2};
      --brand-h3: ${fs.h3};
      --brand-line-height: ${theme.lineSpacing};
      --brand-section-gap: ${theme.sectionSpacing}px;
      --brand-margin-top: ${m.top}px;
      --brand-margin-right: ${m.right}px;
      --brand-margin-bottom: ${m.bottom}px;
      --brand-margin-left: ${m.left}px;
    }
    ${s} > .rp-root {
      font-family: var(--brand-font);
      line-height: var(--brand-line-height);
      ${needsPadding ? `padding-top: var(--brand-margin-top); padding-right: var(--brand-margin-right); padding-bottom: var(--brand-margin-bottom); padding-left: var(--brand-margin-left);` : ''}
    }
  `;
}

/** Render a resume with the template chosen by `resume.template`. */
export function ResumePreview({ resume }: { resume: PreviewInput }) {
  const Template = templateMap[resume.template || 'classic'] || ClassicTemplate;
  const scopeId = useId().replace(/:/g, '');
  const data = toTemplateResume(resume);
  // Deep-merge margin so a themeConfig whose `margin` is missing/undefined
  // (older backend records) doesn't crash buildThemeCSS reading `m.top`.
  const theme: ThemeConfig = {
    ...DEFAULT_THEME,
    ...(data.themeConfig || {}),
    margin: { ...DEFAULT_THEME.margin, ...(data.themeConfig?.margin || {}) },
  };
  const tpl = data.template || 'classic';

  return (
    <>
      <link rel="preconnect" href="https://fonts.googleapis.com" />
      <link rel="preconnect" href="https://fonts.gstatic.com" crossOrigin="" />
      <link
        href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Noto+Sans+SC:wght@300;400;500;600;700&display=swap"
        rel="stylesheet"
      />
      <div data-theme-scope={scopeId}>
        <style dangerouslySetInnerHTML={{ __html: buildThemeCSS(scopeId, theme, tpl) }} />
        <Template resume={data} />
      </div>
    </>
  );
}
