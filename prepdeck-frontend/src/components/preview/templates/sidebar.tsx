import type { TemplateResume } from '../../../types/preview';
import { renderTemplate, PRESETS } from '../_shared';

export function SidebarTemplate({ resume }: { resume: TemplateResume }) {
  return renderTemplate(resume, PRESETS['sidebar']);
}
