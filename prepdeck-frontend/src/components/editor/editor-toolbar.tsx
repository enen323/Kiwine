import { useState, useRef, useEffect, type ElementType } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Undo2,
  Redo2,
  Download,
  Sparkles,
  Palette,
  Save,
  ChevronDown,
  FileText,
  FileJson,
  FileCode,
  File,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useEditorStore } from '../../stores/editor-store';
import { useResumeStore } from '../../stores/resume-store';
import { resumeApi } from '../../api/resume';
import type { ExportFormat } from '../../api/resume';
import { exportResumePdf } from '../../lib/export-pdf';
import { useAiPolish } from '../../hooks/useAiPolish';
import { toast } from 'sonner';

type ToolbarExportFormat = ExportFormat | 'pdf';

const EXPORT_FORMATS: { value: ToolbarExportFormat; label: string; icon: ElementType }[] = [
  { value: 'pdf', label: 'PDF', icon: File },
  { value: 'html', label: 'HTML', icon: FileCode },
  { value: 'txt', label: '纯文本', icon: FileText },
  { value: 'json', label: 'JSON', icon: FileJson },
];

export function EditorToolbar() {
  const navigate = useNavigate();
  const { undo, redo, undoStack, redoStack, toggleThemeEditor, showThemeEditor } = useEditorStore();
  const { isSaving, isDirty, currentResume, sections, setTitle, save, applySnapshot } = useResumeStore();
  const { polishingAll, polishAll } = useAiPolish();
  const [editingTitle, setEditingTitle] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const exportRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (exportRef.current && !exportRef.current.contains(e.target as Node)) setExportOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleUndo = () => {
    const snapshot = undo();
    if (snapshot) applySnapshot(snapshot.sections);
  };
  const handleRedo = () => {
    const snapshot = redo();
    if (snapshot) applySnapshot(snapshot.sections);
  };

  const handleExport = async (format: ToolbarExportFormat) => {
    setExportOpen(false);
    if (!currentResume?.id) return;

    // PDF is generated entirely on the frontend from the LIVE preview content
    // (currentResume metadata + the latest edited sections), reusing the exact
    // same template components as the preview — so the export matches 1:1.
    if (format === 'pdf') {
      exportResumePdf(
        {
          title: currentResume.title,
          template: currentResume.template,
          language: currentResume.language,
          themeConfig: currentResume.themeConfig,
          sections,
        },
        () => toast.success('已生成 PDF，请在打印窗口中保存'),
      );
      return;
    }

    try {
      const blob = (await resumeApi.exportResume(currentResume.id, format)).data;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${currentResume.title || 'resume'}.${format}`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success('导出成功');
    } catch (err) {
      console.error('Export failed', err);
      toast.error('导出失败，请重试');
    }
  };

  const statusText = isSaving ? '保存中…' : isDirty ? '未保存' : '已自动保存';
  const saveStatusClass = isDirty
    ? 'bg-amber-500/10 text-amber-600'
    : 'bg-brand/10 text-brand';

  return (
    <div className="flex h-[52px] flex-shrink-0 items-center justify-between gap-4 border-b bg-card px-4">
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" onClick={() => navigate('/resumes')} title="返回">
          <ArrowLeft />
        </Button>
        {editingTitle ? (
          <input
            className="min-w-[200px] rounded-md border border-brand bg-background px-2 py-1 text-[15px] font-semibold outline-none"
            autoFocus
            value={currentResume?.title || ''}
            onChange={(e) => setTitle(e.target.value)}
            onBlur={() => setEditingTitle(false)}
            onKeyDown={(e) => { if (e.key === 'Enter') setEditingTitle(false); }}
          />
        ) : (
          <span
            className="max-w-[280px] cursor-text truncate text-[15px] font-semibold"
            onClick={() => setEditingTitle(true)}
            title="点击重命名"
          >
            {currentResume?.title || '未命名简历'}
          </span>
        )}
        <span className={`rounded-full px-2 py-0.5 text-xs ${saveStatusClass}`}>{statusText}</span>
        {isDirty && !isSaving && (
          <button type="button" className="inline-flex items-center gap-1 text-sm text-brand hover:text-brand/80" onClick={() => save()}>
            <Save size={12} />
            保存
          </button>
        )}
      </div>

      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon" onClick={handleUndo} disabled={undoStack.length === 0} title="撤销">
          <Undo2 />
        </Button>
        <Button variant="ghost" size="icon" onClick={handleRedo} disabled={redoStack.length === 0} title="重做">
          <Redo2 />
        </Button>

        <span className="mx-1 h-[22px] w-px bg-border" />

        <div className="relative" ref={exportRef}>
          <Button variant="outline" size="sm" onClick={() => setExportOpen((v) => !v)} title="导出">
            <Download />
            <span>导出</span>
            <ChevronDown className="size-4" />
          </Button>
          {exportOpen && (
            <div className="absolute top-[calc(100%+6px)] right-0 z-20 min-w-[140px] rounded-md border bg-card p-1.5 shadow-md">
              {EXPORT_FORMATS.map((f) => {
                const Icon = f.icon;
                return (
                  <button
                    key={f.value}
                    type="button"
                    className="flex w-full items-center gap-2 rounded-md px-2.5 py-2 text-left text-sm text-foreground hover:bg-muted"
                    onClick={() => handleExport(f.value)}
                  >
                    <Icon className="size-3.5" />
                    {f.label}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        <Button
          variant="outline"
          size="sm"
          onClick={polishAll}
          disabled={polishingAll}
          title="AI 润色全部模块"
          className="border-brand/40 text-brand hover:bg-brand/10 hover:text-brand"
        >
          <Sparkles className={polishingAll ? 'animate-pulse' : ''} />
          <span>{polishingAll ? '润色中…' : 'AI 润色'}</span>
        </Button>

        <Button
          variant={showThemeEditor ? 'default' : 'outline'}
          size="sm"
          onClick={toggleThemeEditor}
          title="主题与样式"
          className={showThemeEditor ? 'border-brand bg-brand text-brand-foreground hover:bg-brand/90' : ''}
        >
          <Palette />
          <span>主题</span>
        </Button>
      </div>
    </div>
  );
}
