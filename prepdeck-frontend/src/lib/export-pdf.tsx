// Frontend PDF export that reuses the exact same template components as the
// live preview (per the JadeAI feature spec: "导出模块与预览模块共用同一套组件代码").
//
// Strategy: render <ResumePreview> into a detached container with flushSync
// (so the DOM is ready synchronously), then pipe the rendered markup into an
// off-screen <iframe> and trigger the browser's print dialog (Save as PDF).
// No extra dependency (no html2canvas / jsPDF) and the output matches the
// on-screen preview pixel-for-pixel.
//
// IMPORTANT: the whole [data-theme-scope] subtree must be captured — it holds
// the injected <style> that defines the --brand-* CSS variables. Grabbing only
// .rp-root silently drops the theme and produces an unstyled black-on-white PDF.

import { createRoot } from 'react-dom/client';
import { flushSync } from 'react-dom';
import { ResumePreview } from '../components/preview/resume-preview';
import type { EditorSection, ThemeConfig } from '../types/editor';

export interface PdfResumeInput {
  title?: string;
  template?: string;
  language?: string;
  themeConfig?: ThemeConfig | null;
  sections?: EditorSection[];
}

const FONTS = `
  <link rel="preconnect" href="https://fonts.googleapis.com" />
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&family=Noto+Sans+SC:wght@300;400;500;600;700&display=swap" rel="stylesheet" />
`;

// print-color-adjust: exact forces the browser to honor background colors
// even when the user leaves "Background graphics" unchecked — this is what
// makes dark/colored templates (neon, watercolor, creative, …) export correctly.
const PRINT_CSS = `
  @page { size: A4; margin: 0; }
  html, body {
    margin: 0;
    padding: 0;
    background: #ffffff;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
  }
  * {
    -webkit-print-color-adjust: exact !important;
    print-color-adjust: exact !important;
  }
  .rp-root { box-shadow: none !important; margin: 0 auto; }
  /* Keep a section from splitting awkwardly across two pages. */
  [data-section] { break-inside: avoid; }
  [data-section] > *:first-child { break-after: avoid; }
`;

/** Print an already-rendered DOM node (e.g. the mounted preview root). */
export function printElement(el: HTMLElement, title?: string, onDone?: () => void): void {
  const safeTitle = (title || 'resume').replace(/[<>"]/g, '').trim() || 'resume';
  const html = `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${safeTitle}</title>${FONTS}<style>${PRINT_CSS}</style></head><body>${el.outerHTML}</body></html>`;
  const iframe = document.createElement('iframe');
  iframe.style.cssText = 'position:fixed;right:0;bottom:0;width:0;height:0;border:0;';
  iframe.srcdoc = html;
  document.body.appendChild(iframe);

  const doPrint = () => {
    try {
      iframe.contentWindow?.focus();
      iframe.contentWindow?.print();
    } catch {
      /* user cancelled or print unavailable */
    }
  };

  iframe.onload = () => {
    const doc = iframe.contentDocument;
    // Wait for web fonts to load so the PDF matches the preview typography.
    // Cap the wait so an offline / slow network never blocks the dialog.
    if (doc?.fonts?.ready) {
      Promise.race([
        doc.fonts.ready,
        new Promise((resolve) => setTimeout(resolve, 1500)),
      ]).then(doPrint);
    } else {
      doPrint();
    }
    // Remove the iframe shortly after the dialog opens.
    setTimeout(() => {
      iframe.remove();
      onDone?.();
    }, 800);
  };
}

/**
 * Render the resume with its template and open the print dialog (Save as PDF).
 * @param onExported called once the print dialog has been opened/closed so the
 *        caller can surface a success toast ("导出成功").
 */
export function exportResumePdf(resume: PdfResumeInput, onExported?: () => void): void {
  const container = document.createElement('div');
  container.style.position = 'fixed';
  container.style.left = '-99999px';
  container.style.top = '0';
  document.body.appendChild(container);
  const root = createRoot(container);
  flushSync(() => {
    root.render(<ResumePreview resume={resume} />);
  });

  // Capture the whole theme scope (includes the injected <style> with the
  // --brand-* variables). Falls back to .rp-root if the scope is missing.
  const scopeEl =
    (container.querySelector('[data-theme-scope]') as HTMLElement | null) ??
    (container.querySelector('.rp-root') as HTMLElement | null);
  if (!scopeEl) {
    root.unmount();
    container.remove();
    return;
  }
  printElement(scopeEl, resume.title, () => {
    onExported?.();
    root.unmount();
    container.remove();
  });
}
