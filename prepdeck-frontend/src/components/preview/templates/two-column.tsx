import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function TwoColumnTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['two-column']);
}
