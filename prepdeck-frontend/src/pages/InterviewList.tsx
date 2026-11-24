import { useState, useEffect, useReducer } from 'react';
import { useNavigate } from 'react-router-dom';
import { Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { interviewApi } from '../api/interview';
import {
  CompanyEnum, PositionEnum, InterviewStatusMap,
  type InterviewVO, type CompanyType, type InterviewStatus, type PositionType,
} from '../types';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { TourOverlay } from '@/components/tour/tour-overlay';
import { useTourTrigger } from '@/hooks/use-tour';
import { TOUR_IDS, INTERVIEWS_TOUR_STEPS } from '@/lib/tour-steps';

type State = {
  interviews: InterviewVO[];
  loading: boolean;
  total: number;
  error: string;
};

const statusVariant = (status: string): 'secondary' | 'default' | 'outline' | 'destructive' => {
  switch (status) {
    case 'IN_PROGRESS': return 'default';
    case 'COMPLETED': return 'outline';
    case 'TERMINATED': return 'destructive';
    default: return 'secondary';
  }
};

export default function InterviewList() {
  const navigate = useNavigate();
  const [companyFilter, setCompanyFilter] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [page, setPage] = useState(1);
  const pageSize = 10;

  const [state, dispatch] = useReducer(
    (s: State, a: Partial<State>) => ({ ...s, ...a }),
    { interviews: [], loading: true, total: 0, error: '' },
  );
  const { interviews, loading, total, error } = state;

  /** 待删除的面试记录（用于确认弹窗），null 表示弹窗关闭 */
  const [deleteTarget, setDeleteTarget] = useState<InterviewVO | null>(null);
  const [deleting, setDeleting] = useState(false);

  const fetchList = async () => {
    dispatch({ loading: true, error: '' });
    try {
      const res = await interviewApi.list({
        company: (companyFilter || undefined) as CompanyType | undefined,
        status: (statusFilter || undefined) as InterviewStatus | undefined,
        page, pageSize,
      });
      const body = res.data;
      let list: InterviewVO[] = [];
      let totalCount = 0;
      const extract = (d: unknown): void => {
        if (Array.isArray(d)) { list = d; return; }
        if (!d || typeof d !== 'object') return;
        const o = d as Record<string, unknown>;
        for (const key of ['data', 'records', 'content', 'list', 'items', 'rows']) {
          const v = o[key];
          if (Array.isArray(v)) { list = v as InterviewVO[]; totalCount = (o.total ?? o.totalElements ?? o.totalCount ?? 0) as number; return; }
        }
      };
      extract(body?.data);
      if (list.length === 0) extract(body);
      dispatch({ interviews: list, total: totalCount || list.length, loading: false });
    } catch { dispatch({ error: '加载失败，请检查网络', loading: false }); }
  };
  useEffect(() => { fetchList(); }, [page, companyFilter, statusFilter]);

  // 新手引导：列表加载完成后触发，重点讲「开始 AI 模拟面试」
  useTourTrigger(TOUR_IDS.interviews, INTERVIEWS_TOUR_STEPS.length, { ready: !loading });

  const handleStartNew = () => navigate('/interviews/start');

  /** 确认删除：成功后刷新列表（本前端无本地缓存，重新拉取即可清空） */
  const handleDelete = async () => {
    if (!deleteTarget) return;
    const target = deleteTarget;
    setDeleting(true);
    try {
      await interviewApi.delete(target.id);
      toast.success('面试记录已删除');
      setDeleteTarget(null);
      fetchList();
    } catch (err) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      // 404：记录不存在或已删除（接口幂等）——同步列表状态即可
      if (status === 404) {
        toast.info('该面试记录不存在或已删除');
        setDeleteTarget(null);
        fetchList();
      } else {
        const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
          || '删除失败，请稍后重试';
        toast.error(msg);
      }
    } finally {
      setDeleting(false);
    }
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <div className="mb-8">
        <h1 className="text-2xl font-semibold tracking-tight text-foreground">面试记录</h1>
        <p className="mt-1 text-muted-foreground">查看过往面试记录和评分</p>
      </div>

      <div className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div data-tour="interview-filters" className="flex flex-wrap items-center gap-3">
          <Select
            value={companyFilter}
            onValueChange={(v) => { setCompanyFilter(v === '__all__' ? '' : v); setPage(1); }}
          >
            <SelectTrigger className="w-[160px] rounded-md">
              <SelectValue placeholder="全部公司" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">全部公司</SelectItem>
              {Object.entries(CompanyEnum).map(([val, label]) => (
                <SelectItem key={val} value={val}>{label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Select
            value={statusFilter}
            onValueChange={(v) => { setStatusFilter(v === '__all__' ? '' : v); setPage(1); }}
          >
            <SelectTrigger className="w-[160px] rounded-md">
              <SelectValue placeholder="全部状态" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="__all__">全部状态</SelectItem>
              {Object.entries(InterviewStatusMap).map(([val, label]) => (
                <SelectItem key={val} value={val}>{label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button data-tour="interview-start" onClick={handleStartNew}>+ 开始面试</Button>
      </div>

      {error && (
        <div className="mb-4 rounded-full bg-destructive px-5 py-2 text-center text-sm text-white">{error}</div>
      )}

      {loading ? (
        <div className="py-16 text-center text-muted-foreground">加载中...</div>
      ) : interviews.length === 0 ? (
        <div className="flex flex-col items-center py-16 text-center">
          <p className="text-foreground">还没有面试记录</p>
          <Button className="mt-6" onClick={handleStartNew}>开始第一次面试</Button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {interviews.map(iv => (
            <Card
              key={iv.id}
              className="cursor-pointer transition-shadow hover:shadow-md"
              onClick={() => navigate(`/interviews/${iv.id}`)}
            >
              <CardContent className="flex items-center justify-between gap-4 py-4">
                <div>
                  <h3 className="font-medium text-foreground">
                    {CompanyEnum[iv.company as CompanyType] || iv.company} · {PositionEnum[iv.position as PositionType] || iv.position}
                  </h3>
                  <p className="mt-1 text-sm text-muted-foreground">
                    {iv.totalQuestions} 个问答 · {iv.startedAt || iv.createdAt}
                    {iv.durationSeconds && ` · ${Math.floor(iv.durationSeconds / 60)}分钟`}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-3">
                  {iv.scoreTotal !== null && (
                    <span className="text-base font-semibold text-brand">{iv.scoreTotal}分</span>
                  )}
                  <Badge variant={statusVariant(iv.status)}>
                    {InterviewStatusMap[iv.status as keyof typeof InterviewStatusMap] || iv.status}
                  </Badge>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="text-muted-foreground hover:text-destructive"
                    aria-label="删除面试记录"
                    onClick={(e) => {
                      e.stopPropagation();
                      setDeleteTarget(iv);
                    }}
                  >
                    <Trash2 className="size-4" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Pagination */}
      {total > pageSize && (
        <div className="mt-8 flex items-center justify-center gap-3">
          <Button variant="ghost" disabled={page <= 1} onClick={() => setPage(p => p - 1)}>
            上一页
          </Button>
          <span className="text-sm text-muted-foreground">{page} / {Math.ceil(total / pageSize)}</span>
          <Button variant="ghost" disabled={page >= Math.ceil(total / pageSize)} onClick={() => setPage(p => p + 1)}>
            下一页
          </Button>
        </div>
      )}

      {/* 删除确认弹窗 */}
      <AlertDialog
        open={!!deleteTarget}
        onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除面试记录</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除「
              {deleteTarget
                ? `${CompanyEnum[deleteTarget.company as CompanyType] || deleteTarget.company} · ${PositionEnum[deleteTarget.position as PositionType] || deleteTarget.position}`
                : ''}
              」的面试记录吗？此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <Button variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? '删除中...' : '删除'}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 新手引导 */}
      <TourOverlay tourId={TOUR_IDS.interviews} steps={INTERVIEWS_TOUR_STEPS} />
    </div>
  );
}
