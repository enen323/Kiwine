import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function MaterialTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['material']);
}
