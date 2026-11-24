import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { resumeApi, type ExportFormat } from '../api/resume';
import type { ResumeVO, EditorSection } from '../types';
import type { SectionType } from '../types/editor';
import { parseSectionContent, generateId, normalizeSectionTitle } from '../lib/editor-utils';
import { ResumePreview } from '../components/preview/resume-preview';
import { exportResumePdf } from '../lib/export-pdf';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';

export default function Preview() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [resume, setResume] = useState<ResumeVO | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pdfBusy, setPdfBusy] = useState(false);

  useEffect(() => {
    if (!id) return;
    resumeApi.getById(id)
      .then(res => setResume(res.data.data))
      .catch(() => setError('加载失败'))
      .finally(() => setLoading(false));
  }, [id]);

  // The API view-model stores each section's `content` as a JSON string.
  // Normalize it into the structured EditorSection[] the templates expect.
  const sections: EditorSection[] = useMemo(() => {
    if (!resume?.sections) return [];
    return resume.sections.map((s, i) => ({
      id: String(s.id ?? generateId()),
      type: (s.type as SectionType) ?? 'custom',
      title: normalizeSectionTitle((s.type as SectionType) ?? 'custom', s.title),
      order: typeof (s as { sortOrder?: number }).sortOrder === 'number'
        ? (s as { sortOrder?: number }).sortOrder!
        : i,
      visible: s.visible !== false,
      content: parseSectionContent(s.type ?? 'custom', s.content),
    }));
  }, [resume]);

  const handleExport = async (format: ExportFormat) => {
    if (!id) return;
    try {
      const res = await resumeApi.exportResume(id, format);
      const blob = res.data;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${resume?.title || resume?.fileName || 'resume'}.${format}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success('导出成功');
    } catch {
      toast.error('导出失败，请重试');
    }
  };

  const handleExportPdf = () => {
    if (!resume || pdfBusy) return;
    setPdfBusy(true);
    try {
      exportResumePdf(
        {
          title: resume.title,
          template: resume.template,
          language: resume.language,
          themeConfig: resume.themeConfig,
          sections,
        },
        () => toast.success('已生成 PDF，请在打印窗口中保存'),
      );
    } finally {
      // The print dialog is async; release the button shortly after.
      setTimeout(() => setPdfBusy(false), 1500);
    }
  };

  if (loading) {
    return <div className="mx-auto max-w-7xl px-4 py-8"><div className="py-16 text-center text-muted-foreground">加载中...</div></div>;
  }
  if (error || !resume) {
    return <div className="mx-auto max-w-7xl px-4 py-8"><div className="py-16 text-center text-muted-foreground">简历不存在或加载失败</div></div>;
  }

  return (
    <div className="flex flex-col" style={{ height: 'calc(100vh - 56px)' }}>
      <div className="flex items-center justify-between border-b bg-background px-4 py-3">
        <button
          className="flex items-center gap-1 text-sm text-muted-foreground transition-colors hover:text-foreground"
          onClick={() => navigate(`/resumes/${id}`)}
        >
          ← 返回编辑
        </button>
        <div className="flex items-center gap-2">
          <Button onClick={handleExportPdf} disabled={pdfBusy}>
            {pdfBusy ? '正在导出…' : '导出 PDF'}
          </Button>
          <Button variant="ghost" onClick={() => handleExport('html')}>导出 HTML</Button>
          <Button variant="ghost" onClick={() => handleExport('txt')}>导出 TXT</Button>
          <Button variant="ghost" onClick={() => handleExport('json')}>导出 JSON</Button>
        </div>
      </div>

      <div className="flex-1 overflow-auto bg-muted/30 p-6">
        <ResumePreview
          resume={{
            title: resume.title,
            template: resume.template,
            language: resume.language,
            themeConfig: resume.themeConfig,
            sections,
          }}
        />
      </div>
    </div>
  );
}
