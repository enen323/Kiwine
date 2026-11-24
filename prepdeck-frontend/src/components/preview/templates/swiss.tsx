import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function SwissTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['swiss']);
}
