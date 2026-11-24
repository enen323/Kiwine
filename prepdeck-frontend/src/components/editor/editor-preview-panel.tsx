import { Component, useState, type CSSProperties, type ReactNode } from 'react';
import { ZoomIn, ZoomOut, Eye } from 'lucide-react';
import { useResumeStore } from '../../stores/resume-store';
import { ResumePreview } from '../preview/resume-preview';
import { Button } from '@/components/ui/button';
import { ScrollArea } from '@/components/ui/scroll-area';

// A4 width in px (at 96 dpi) — the natural width of the rendered resume sheet.
const A4_WIDTH = 794;

/**
 * Error boundary so a misbehaving template can never blank out the whole
 * editor. Mirrors JadeAI's PreviewErrorBoundary but keeps the editor alive.
 */
class PreviewErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };
  static getDerivedStateFromError() {
    return { hasError: true };
  }
  render() {
    if (this.state.hasError) {
      return (
        <div className="p-6 text-center text-sm text-destructive">
          预览渲染出错，请检查简历内容。
        </div>
      );
    }
    return this.props.children;
  }
}

/**
 * JadeAI 风格的实时预览面板。右侧常驻，编辑左侧内容时此处随 store 中的
 * sections / theme 实时刷新。使用 ShadCN + Tailwind 重排。
 */
export function EditorPreviewPanel() {
  const currentResume = useResumeStore((s) => s.currentResume);
  const sections = useResumeStore((s) => s.sections);
  const [zoom, setZoom] = useState(70);

  if (!currentResume) return null;

  // Live resume = current resume metadata + the latest edited sections.
  const liveResume = {
    title: currentResume.title,
    template: currentResume.template,
    language: currentResume.language,
    themeConfig: currentResume.themeConfig,
    sections,
  };

  // `zoom` is the established scaling approach in this project (see editor-canvas).
  const sheetStyle = {
    width: A4_WIDTH,
    zoom: zoom / 100,
  } as CSSProperties;

  return (
    <div className="flex h-full w-full flex-col">
      <div className="flex items-center justify-between border-b bg-background px-4 py-2">
        <span className="flex items-center gap-1.5 text-sm font-medium text-foreground">
          <Eye size={14} className="text-brand" />
          实时预览
        </span>
        <div className="flex items-center gap-1">
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setZoom((z) => Math.max(30, z - 10))}
            disabled={zoom <= 30}
            title="缩小"
          >
            <ZoomOut size={16} />
          </Button>
          <span className="w-10 text-center text-xs tabular-nums text-muted-foreground">
            {zoom}%
          </span>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => setZoom((z) => Math.min(150, z + 10))}
            disabled={zoom >= 150}
            title="放大"
          >
            <ZoomIn size={16} />
          </Button>
        </div>
      </div>

      <ScrollArea className="min-h-0 flex-1">
        <div className="flex justify-center p-6">
          <div style={sheetStyle} className="bg-white shadow-sm ring-1 ring-black/5">
            <PreviewErrorBoundary>
              <ResumePreview resume={liveResume} />
            </PreviewErrorBoundary>
          </div>
        </div>
      </ScrollArea>
    </div>
  );
}
