import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function MagazineTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['magazine']);
}
