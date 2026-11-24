import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function MedicalTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['medical']);
}
