// Bridge types for the preview / template rendering layer.
// Templates consume a `TemplateResume` — a thin view over PrepDeck's own resume
// model (ResumeVO + EditorSection), so no separate data shape is needed.

import type { EditorSection, ThemeConfig } from './editor';

export interface TemplateResume {
  id?: string | number;
  title?: string;
  template?: string;
  language?: string;
  themeConfig?: ThemeConfig | null;
  sections: EditorSection[];
}
