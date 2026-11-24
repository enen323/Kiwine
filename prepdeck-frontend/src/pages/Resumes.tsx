import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { Sparkles } from 'lucide-react';
import { resumeApi, normalizeResumeList, type ExportFormat } from '../api/resume';
import type { ResumeVO } from '../types';
import { TEMPLATE_LABELS } from '@/lib/constants';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { GenerateResumeDialog } from '@/components/generate/generate-resume-dialog';
import { CreateResumeDialog } from '@/components/resume/create-resume-dialog';
import { TourOverlay } from '@/components/tour/tour-overlay';
import { useTourTrigger } from '@/hooks/use-tour';
import { TOUR_IDS, RESUMES_TOUR_STEPS } from '@/lib/tour-steps';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuTrigger,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
} from '@/components/ui/dropdown-menu';

// 模板 key → 中文名（与后端 template 字段对应；未知则原样显示）
const templateLabel = (t?: string) => (t ? (TEMPLATE_LABELS[t] ?? t) : '经典');

export default function Resumes() {
  const navigate = useNavigate();
  const [resumes, setResumes] = useState<ResumeVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState('');

  // 分页 + 搜索（对应新后端 Result<PageResult<ResumeVO>>：pageData.data=列表、pageData.total=总数）
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize] = useState(10);
  const [keyword, setKeyword] = useState('');

  // AI 生成简历弹窗
  const [showGenerate, setShowGenerate] = useState(false);

  // Create modal state（表单与创建逻辑都在 CreateResumeDialog 内部，弹窗关闭即重置）
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Delete confirm state
  const [deleteTarget, setDeleteTarget] = useState<ResumeVO | null>(null);
  const [deleting, setDeleting] = useState(false);

  const fetchResumes = async (overrides?: { page?: number; keyword?: string }) => {
    setFetching(true);
    setError('');
    try {
      const p = overrides?.page ?? page;
      const kw = overrides?.keyword ?? keyword;
      const res = await resumeApi.list({ keyword: kw || undefined, page: p, pageSize });
      const { list, total: totalCount } = normalizeResumeList(res.data);
      setResumes(list);
      setTotal(totalCount);
      if (overrides?.page !== undefined) setPage(overrides.page);
      if (overrides?.keyword !== undefined) setKeyword(overrides.keyword);
    } catch {
      setError('加载失败，请检查网络');
    } finally {
      setFetching(false);
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchResumes();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 新手引导：列表加载完成后再触发，保证「简历卡片」这一步的目标元素已渲染
  useTourTrigger(TOUR_IDS.resumes, RESUMES_TOUR_STEPS.length, { ready: !loading });

  // 搜索防抖
  const searchTimer = useRef<number | undefined>(undefined);
  const handleSearchChange = (v: string) => {
    setKeyword(v);
    if (searchTimer.current) window.clearTimeout(searchTimer.current);
    searchTimer.current = window.setTimeout(() => {
      fetchResumes({ page: 1, keyword: v });
    }, 300);
  };

  const handleSetActive = async (id: string | number) => {
    try {
      await resumeApi.setActive(id);
      fetchResumes();
    } catch {
      alert('设置失败');
    }
  };

  const handleDuplicate = async (id: string | number) => {
    try {
      await resumeApi.duplicate(id);
      fetchResumes();
    } catch {
      alert('复制失败');
    }
  };

  const handleExport = async (r: ResumeVO, format: ExportFormat) => {
    try {
      const res = await resumeApi.exportResume(r.id, format);
      const blob = res.data;
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${(r.title || r.fileName || 'resume')}.${format}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
      toast.success('导出成功');
    } catch {
      toast.error('导出失败，请重试');
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await resumeApi.delete(deleteTarget.id);
      setDeleteTarget(null);
      // 删掉当前页最后一条且不在第一页时，回退一页
      if (resumes.length === 1 && page > 1) {
        fetchResumes({ page: page - 1 });
      } else {
        fetchResumes();
      }
    } catch {
      alert('删除失败');
    } finally {
      setDeleting(false);
    }
  };

  const previewText = (r: ResumeVO) => {
    if (r.contentText) return r.contentText.slice(0, 120);
    if (r.sections?.length) return r.sections.map(s => s.title || s.type || '').filter(Boolean).join(' · ');
    return '无文本内容';
  };

  const wordCount = (r: ResumeVO) => (r.contentText ? r.contentText.length : 0);

  const totalPages = Math.max(1, Math.ceil(total / pageSize));

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">我的简历</h1>
        <p className="mt-1 text-muted-foreground">管理你的简历，AI 面试官将基于当前使用的简历提问</p>
      </div>

      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-1 items-center gap-3">
          <Input
            data-tour="resume-search"
            type="search"
            placeholder="搜索简历标题 / 文件名"
            value={keyword}
            disabled={fetching}
            onChange={e => handleSearchChange(e.target.value)}
            className="max-w-sm"
          />
          {total > 0 && <span className="shrink-0 text-sm text-muted-foreground">共 {total} 份</span>}
        </div>
        <Button data-tour="resume-create" onClick={() => setShowCreateModal(true)}>+ 新建简历</Button>
        <Button
          data-tour="resume-ai-generate"
          variant="outline"
          onClick={() => setShowGenerate(true)}
          className="border-brand/40 text-brand hover:bg-brand/10 hover:text-brand"
        >
          <Sparkles className="mr-1.5 size-4" />
          AI 生成简历
        </Button>
      </div>

      {error && (
        <div className="mb-4 rounded-full bg-destructive px-5 py-2 text-center text-sm text-white">{error}</div>
      )}

      {loading && resumes.length === 0 ? (
        <div className="py-16 text-center text-muted-foreground">加载中...</div>
      ) : resumes.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <p className="text-base text-foreground">还没有简历</p>
          <p className="mb-6 mt-2 text-sm text-muted-foreground">点击新建，开始创建你的第一份简历</p>
          <Button onClick={() => setShowCreateModal(true)}>创建简历</Button>
        </div>
      ) : (
        <>
          <div className="flex flex-col gap-4">
            {resumes.map((r, idx) => (
              <Card
                key={String(r.id)}
                /* 引导只高亮第一张卡片，避免整列表被聚光灯框住 */
                data-tour={idx === 0 ? 'resume-list' : undefined}
                className={cn('cursor-pointer transition-shadow hover:shadow-md', r.isActive && 'ring-2 ring-brand')}
                onClick={() => navigate(`/resumes/${r.id}`)}
              >
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between gap-2">
                    <CardTitle className="text-base">
                      {r.title || r.fileName || '未命名简历'}
                    </CardTitle>
                    <DropdownMenu>
                      <DropdownMenuTrigger asChild>
                        <Button
                          variant="ghost"
                          size="icon"
                          type="button"
                          aria-label="更多操作"
                          className="size-8"
                          onClick={(e) => e.stopPropagation()}
                        >
                          ⋯
                        </Button>
                      </DropdownMenuTrigger>
                      <DropdownMenuContent onClick={(e) => e.stopPropagation()}>
                        <DropdownMenuItem onClick={() => navigate(`/resumes/${r.id}`)}>
                          编辑
                        </DropdownMenuItem>
                        {!r.isActive && (
                          <DropdownMenuItem onClick={() => handleSetActive(r.id)}>
                            设为当前使用
                          </DropdownMenuItem>
                        )}
                        <DropdownMenuItem onClick={() => handleDuplicate(r.id)}>
                          复制副本
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleExport(r, 'html')}>
                          导出 HTML
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleExport(r, 'txt')}>
                          导出 TXT
                        </DropdownMenuItem>
                        <DropdownMenuItem onClick={() => handleExport(r, 'json')}>
                          导出 JSON
                        </DropdownMenuItem>
                        <DropdownMenuSeparator />
                        <DropdownMenuItem variant="destructive" onClick={() => setDeleteTarget(r)}>
                          删除
                        </DropdownMenuItem>
                      </DropdownMenuContent>
                    </DropdownMenu>
                  </div>
                  <div className="flex items-center gap-2">
                    {r.isActive && <Badge className="bg-brand text-brand-foreground hover:bg-brand/90">使用中</Badge>}
                    <Badge variant="secondary">{templateLabel(r.template)}</Badge>
                  </div>
                </CardHeader>
                <CardContent>
                  <div className="mb-2 text-xs text-muted-foreground">
                    更新于 {r.updateTime} · {wordCount(r)} 字
                  </div>
                  <p className="line-clamp-3 text-sm text-muted-foreground">{previewText(r)}</p>
                </CardContent>
              </Card>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="mt-8 flex items-center justify-center gap-3">
              <Button variant="ghost" disabled={fetching || page <= 1} onClick={() => fetchResumes({ page: page - 1 })}>
                上一页
              </Button>
              <span className="text-sm text-muted-foreground">第 {page} / {totalPages} 页</span>
              <Button variant="ghost" disabled={fetching || page >= totalPages} onClick={() => fetchResumes({ page: page + 1 })}>
                下一页
              </Button>
            </div>
          )}
        </>
      )}

      {/* Create Resume Modal（缩略图模板库版本，逻辑封装在组件内部） */}
      <CreateResumeDialog
        open={showCreateModal}
        onOpenChange={setShowCreateModal}
        onCreated={() => fetchResumes()}
      />

      {/* Delete Confirm Modal */}
      <Dialog
        open={!!deleteTarget}
        onOpenChange={(open) => { if (!open && !deleting) setDeleteTarget(null); }}
      >
        <DialogContent className="max-w-[400px]">
          <DialogHeader>
            <DialogTitle>删除简历</DialogTitle>
          </DialogHeader>
          <DialogDescription>
            确定删除「{deleteTarget?.title || deleteTarget?.fileName || '未命名简历'}」吗？此操作不可恢复。
          </DialogDescription>
          <DialogFooter>
            <Button type="button" variant="ghost" onClick={() => setDeleteTarget(null)} disabled={deleting}>
              取消
            </Button>
            <Button type="button" variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? '删除中...' : '确认删除'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* AI 生成简历弹窗 */}
      <GenerateResumeDialog open={showGenerate} onOpenChange={setShowGenerate} />

      {/* 新手引导 */}
      <TourOverlay tourId={TOUR_IDS.resumes} steps={RESUMES_TOUR_STEPS} />
    </div>
  );
}
