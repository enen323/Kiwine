import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function CorporateTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['corporate']);
}
