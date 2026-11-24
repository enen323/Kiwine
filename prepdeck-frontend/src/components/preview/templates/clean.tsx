import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function CleanTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['clean']);
}
