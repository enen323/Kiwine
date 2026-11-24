import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function EuroTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['euro']);
}
