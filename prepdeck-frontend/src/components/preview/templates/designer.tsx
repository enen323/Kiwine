import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function DesignerTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['designer']);
}
