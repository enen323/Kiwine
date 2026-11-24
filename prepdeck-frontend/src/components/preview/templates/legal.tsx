import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function LegalTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['legal']);
}
