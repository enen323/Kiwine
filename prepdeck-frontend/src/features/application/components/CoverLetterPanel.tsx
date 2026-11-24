import { useEffect, useState } from 'react';
import {
  Sparkles,
  Loader2,
  Copy,
  Trash2,
  FileText,
  Languages,
} from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { coverLetterApi } from '../api/coverLetterApi';
import { normalizeApplicationError } from '../utils';
import type {
  AiCoverLetter,
  CoverLetterLanguage,
  JobApplication,
} from '../types';

const LANG_LABELS: Record<CoverLetterLanguage, string> = {
  CHINESE: '中文',
  ENGLISH: 'English',
};

interface CoverLetterPanelProps {
  application: JobApplication;
}

export function CoverLetterPanel({ application }: CoverLetterPanelProps) {
  const [language, setLanguage] = useState<CoverLetterLanguage>('CHINESE');
  const [generating, setGenerating] = useState(false);
  const [result, setResult] = useState<AiCoverLetter | null>(null);

  const [history, setHistory] = useState<AiCoverLetter[]>([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const resumeId = application.resumeId;

  // 按关联简历拉取历史求职信（仅挂载时一次）
  useEffect(() => {
    if (!resumeId) return;
    let active = true;
    Promise.resolve().then(() => {
      if (active) setLoadingHistory(true);
    });
    coverLetterApi
      .listByResume(resumeId)
      .then((list) => {
        if (active) setHistory(list ?? []);
      })
      .catch(() => {
        /* 历史加载失败不阻断 */
      })
      .finally(() => {
        if (active) setLoadingHistory(false);
      });
    return () => {
      active = false;
    };
  }, [resumeId]);

  const refreshHistory = () => {
    if (!resumeId) return;
    coverLetterApi
      .listByResume(resumeId)
      .then((list) => setHistory(list ?? []))
      .catch(() => {});
  };

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const letter = await coverLetterApi.generate(application.id, {
        company: application.company,
        position: application.position,
        outputLanguage: language,
      });
      setResult(letter);
      refreshHistory();
      toast.success('求职信已生成');
    } catch (err) {
      const code = (err as { code?: number })?.code;
      const msg =
        err instanceof Error && err.message
          ? err.message
          : normalizeApplicationError(code ?? -1);
      if (code === 70000) {
        toast.error('本月免费 AI 额度已用完，无法生成求职信');
      } else if (code === 50003) {
        toast.error('AI 服务异常，请稍后重试');
      } else {
        toast.error(msg);
      }
    } finally {
      setGenerating(false);
    }
  };

  const handleCopy = async (body: string) => {
    try {
      await navigator.clipboard.writeText(body);
      toast.success('求职信正文已复制');
    } catch {
      toast.error('复制失败，请手动选择文本复制');
    }
  };

  const handleDelete = async (id: string) => {
    if (!resumeId) return;
    setDeletingId(id);
    try {
      await coverLetterApi.remove(resumeId, id);
      setHistory((prev) => prev.filter((c) => c.id !== id));
      if (result?.id === id) setResult(null);
      toast.success('求职信已删除');
    } catch (err) {
      const msg = err instanceof Error && err.message ? err.message : '删除失败';
      toast.error(msg);
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="mt-4 space-y-4">
      <Separator />
      <div className="flex items-center gap-2">
        <Sparkles className="size-4 text-brand" />
        <h3 className="text-sm font-semibold text-foreground">AI 求职信</h3>
      </div>

      {/* 语言切换 + 生成 */}
      <div className="flex flex-wrap items-center gap-3">
        <div className="inline-flex items-center rounded-lg border p-0.5">
          {(Object.keys(LANG_LABELS) as CoverLetterLanguage[]).map((lang) => (
            <button
              key={lang}
              type="button"
              onClick={() => setLanguage(lang)}
              className={
                'rounded-md px-3 py-1 text-sm transition-colors ' +
                (language === lang
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:text-foreground')
              }
            >
              {LANG_LABELS[lang]}
            </button>
          ))}
        </div>
        <Button
          type="button"
          size="sm"
          onClick={handleGenerate}
          disabled={generating}
        >
          {generating ? (
            <Loader2 className="mr-1 size-4 animate-spin" />
          ) : (
            <Languages className="mr-1 size-4" />
          )}
          {generating ? '生成中（约 3-10 秒）' : '生成求职信'}
        </Button>
      </div>

      {/* 生成结果 */}
      {result && (
        <div className="rounded-lg border bg-muted/30 p-4">
          <div className="mb-2 flex items-start justify-between gap-2">
            <p className="text-sm font-medium text-foreground">{result.title}</p>
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => handleCopy(result.body)}
            >
              <Copy className="mr-1 size-3.5" />
              复制
            </Button>
          </div>
          <div className="max-h-72 overflow-auto whitespace-pre-wrap break-words text-sm leading-relaxed text-foreground">
            {result.body}
          </div>
        </div>
      )}

      {/* 历史求职信（按关联简历） */}
      {resumeId ? (
        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">
            历史求职信（本简历）
          </p>
          {loadingHistory ? (
            <p className="text-xs text-muted-foreground">加载中...</p>
          ) : history.length === 0 ? (
            <p className="text-xs text-muted-foreground">暂无历史求职信</p>
          ) : (
            <ul className="space-y-1.5">
              {history.map((c) => (
                <li
                  key={c.id}
                  className="rounded-lg border px-3 py-2"
                >
                  <div className="flex items-center gap-2">
                    <FileText className="size-3.5 shrink-0 text-muted-foreground" />
                    <button
                      type="button"
                      className="min-w-0 flex-1 truncate text-left text-sm text-foreground hover:underline"
                      onClick={() =>
                        setExpandedId((prev) => (prev === c.id ? null : c.id))
                      }
                    >
                      {c.title}
                    </button>
                    <Button
                      type="button"
                      variant="ghost"
                      size="sm"
                      className="h-7 px-2 text-muted-foreground"
                      disabled={deletingId === c.id}
                      onClick={() => handleDelete(c.id)}
                    >
                      {deletingId === c.id ? (
                        <Loader2 className="size-3.5 animate-spin" />
                      ) : (
                        <Trash2 className="size-3.5" />
                      )}
                    </Button>
                  </div>
                  {expandedId === c.id && (
                    <div className="mt-2 max-h-60 overflow-auto whitespace-pre-wrap break-words text-xs leading-relaxed text-muted-foreground">
                      {c.body}
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <p className="text-xs text-muted-foreground">
          关联简历后可查看该简历下的历史求职信。
        </p>
      )}
    </div>
  );
}
