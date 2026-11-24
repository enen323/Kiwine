import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function ArchitectTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['architect']);
}
