import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function ModernTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['modern']);
}
